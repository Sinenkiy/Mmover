package com.divinegames.mmover

import android.view.Choreographer
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.view.doOnLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MovingBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    @Volatile
    private var bitmap: Bitmap? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var bitmapX = 0f
    private var bitmapY = 0f
    private val tilePaint = Paint()
    private val tileMatrix = Matrix()
    private var tileShader: BitmapShader? = null

    private val choreographer = Choreographer.getInstance()
    private var movementSpeed = 0f
    private var isMoving = false

    private var loadJob: Job? = null
    private var loadRequestId = 0

    private val direction = MovementDirection()
    private var lastFrameTime = 0L

    // Хранит имя загруженного в данный момент фона
    private var hasLoadBeenTriggered = false
    private var currentBackgroundKey: String? = null

    private var isPowerSavingMode: Boolean = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!hasLoadBeenTriggered) {
            loadBackgroundWithCallback {}
        }
    }

    override fun onDetachedFromWindow() {
        stopMovement()
        loadJob?.cancel()
        loadJob = null
        loadRequestId++
        tilePaint.shader = null
        tileShader = null

        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        bitmap = null
        currentBackgroundKey = null
        hasLoadBeenTriggered = false

        super.onDetachedFromWindow()
    }
    /**
     * Асинхронно загружает и подготавливает фон.
     * Теперь он проверяет, нужно ли вообще что-то грузить.
     */
    fun loadBackgroundWithCallback(onLoaded: () -> Unit) {
        hasLoadBeenTriggered = true

        if (width == 0 || height == 0) {
            doOnLayout {
                loadBackgroundWithCallback(onLoaded)
            }
            return
        }

        val currentViewWidth = width
        val currentViewHeight = height

        if (currentViewWidth == 0 || currentViewHeight == 0) {
            Log.e("BG_LOAD", "Критическая ошибка: Ширина или высота View равна 0.")
            return
        }

        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            Log.e("BG_LOAD", "LifecycleOwner не найден, загрузка отменена.")
            return
        }

        loadJob?.cancel()
        val requestId = ++loadRequestId

        loadJob = lifecycleOwner.lifecycleScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val newBackgroundKey = prefs.getString("animation_style", "background_blocks_medium")

            if (newBackgroundKey == currentBackgroundKey && bitmap != null) {
                Log.d("BG_LOAD", "Фон ($newBackgroundKey) уже загружен. Пропускаем.")
                onLoaded()
                return@launch
            }

            Log.d("BG_LOAD", "Загружаю новый фон: $newBackgroundKey")
            logMem("bg_before_decode_$newBackgroundKey")

            val procedural = newBackgroundKey in ProceduralBackground.keys
            // Generate only pixels in the worker: cancellation cannot strand a native bitmap.
            val loadedBitmap = if (procedural) {
                val pixels = withContext(Dispatchers.Default) {
                    ProceduralBackground.generate(requireNotNull(newBackgroundKey))
                }
                Bitmap.createBitmap(pixels, ProceduralBackground.SIZE, ProceduralBackground.SIZE,
                    Bitmap.Config.ARGB_8888)
            } else withContext(Dispatchers.IO) {
                val resourceId = resources.getIdentifier(newBackgroundKey, "drawable", context.packageName)
                if (resourceId == 0) {
                    Log.e("BG_LOAD", "Фон не найден: $newBackgroundKey.")
                    return@withContext createErrorBitmap()
                }

                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeResource(context.resources, resourceId, options)
                    Log.d("BG_LOAD", "Оригинальный размер: ${options.outWidth}x${options.outHeight}")

                    options.inSampleSize = calculateInSampleSize(options, currentViewWidth, currentViewHeight)
                    Log.d("BG_LOAD", "Установлен inSampleSize: ${options.inSampleSize}")

                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    options.inJustDecodeBounds = false

                    val smallBitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
                        ?: return@withContext createErrorBitmap()

                    Log.d("BG_LOAD", "Размер после inSampleSize: ${smallBitmap.width}x${smallBitmap.height}")

                    if (newBackgroundKey == "background_stripes") {
                        val scaledHeight = smallBitmap.height
                        Log.d("BG_LOAD", "Масштабируем Stripes до ${currentViewWidth}x${scaledHeight}")

                        val scaledBitmap = Bitmap.createScaledBitmap(
                            smallBitmap,
                            currentViewWidth,
                            scaledHeight,
                            true
                        )
                        if (scaledBitmap != smallBitmap) {
                            smallBitmap.recycle()
                        }
                        scaledBitmap
                    } else {
                        smallBitmap
                    }
                } catch (e: Exception) {
                    Log.e("BG_LOAD", "Ошибка загрузки изображения: ${e.message}", e)
                    createErrorBitmap()
                }
            }

            logMem("bg_after_decode_$newBackgroundKey")

            if (!isAttachedToWindow || requestId != loadRequestId) {
                Log.d("BG_LOAD", "Результат устарел или View уже detached, освобождаем bitmap")
                if (!loadedBitmap.isRecycled) {
                    loadedBitmap.recycle()
                }
                return@launch
            }

            val oldBitmap = bitmap
            bitmap = loadedBitmap
            tileShader = if (procedural) BitmapShader(loadedBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) else null
            tilePaint.shader = tileShader
            currentBackgroundKey = newBackgroundKey
            bitmapX = 0f
            bitmapY = 0f

            logMem("bg_after_bitmap_assign_$newBackgroundKey")

            if (oldBitmap != null && oldBitmap != loadedBitmap && !oldBitmap.isRecycled) {
                oldBitmap.recycle()
            }

            val sizeMB = bitmap?.byteCount?.toFloat()?.div(1024 * 1024) ?: 0f
            Log.d(
                "BG_LOAD",
                "Финальный размер: ${bitmap?.width}x${bitmap?.height}, занимает: ${String.format("%.2f", sizeMB)} MB"
            )

            invalidate()
            onLoaded()
        }
    }

    /**
     * НОВАЯ, ПРАВИЛЬНАЯ ВЕРСИЯ ФУНКЦИИ РАСЧЕТА
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {

        // --- НОВЫЙ КОД ДЛЯ ПРОВЕРКИ ПЛОТНОСТИ ---
        val density = resources.displayMetrics.density
        val densityDpi = resources.displayMetrics.densityDpi
        Log.d("MyDensity", "--- ПРОВЕРКА ПЛОТНОСТИ ---")
        Log.d("MyDensity", "Коэффициент (density): $density")
        Log.d("MyDensity", "DPI (densityDpi): $densityDpi")
        Log.d("MyDensity", "-------------------------")
        // --- КОНЕЦ НОВОГО КОДА ---

        val (srcH, srcW) = options.outHeight to options.outWidth
        var inSample = 1

        if (srcH > reqHeight || srcW > reqWidth) {
            // во сколько раз источник больше цели по каждой оси
            var magic = density / 2
            if (densityDpi > 460) {           // densityDpi — Int, сравниваем с Int
                magic -= 0.5f                 // вычитаем Float
            }
            magic = magic.coerceAtLeast(1f)
            val ratioH = Math.ceil(srcH.toDouble() * magic / reqHeight).toInt().coerceAtLeast(1)
            val ratioW = Math.ceil(srcW.toDouble() * magic / reqWidth).toInt().coerceAtLeast(1)
            // берём максимум (уменьшаем, если ХОТЯ БЫ ОДНА ось больше нужной)
            var ratio = maxOf(ratioH, ratioW)
            val scaledH = (srcH.toFloat() * density) / ratio.toFloat()
            val scaledW = (srcW.toFloat() * density) / ratio.toFloat()

            if (scaledH < reqHeight.toFloat() || scaledW < reqWidth.toFloat()) {
                ratio = (ratio - 1).coerceAtLeast(1) // эквивалент Math.max(1, ratio - 1)
            }
            // inSampleSize должен быть степенью двойки
            //var pow2 = 1
            //while (pow2 * 2 <= ratio) pow2 *= 2
            //inSample = pow2.coerceAtLeast(1)
            inSample = ratio.coerceAtLeast(1)

        }
        return inSample
    }

    private fun logMem(tag: String) {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val free = rt.freeMemory() / (1024 * 1024)
        val total = rt.totalMemory() / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)

        Log.d("MEM_BG", "$tag used=${used}MB free=${free}MB total=${total}MB max=${max}MB")
    }
    /**
     * Создает маленький битмап-заглушку в случае ошибки.
     */
    private fun createErrorBitmap(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isPowerSavingMode) {
            // Дёшево и сердито: вообще не трогаем bitmap, просто заливаем чёрным.
            canvas.drawColor(Color.BLACK)
            return
        }

        bitmap?.let { bmp ->
            tileShader?.let { shader ->
                tileMatrix.setTranslate(-bitmapX, -bitmapY)
                shader.setLocalMatrix(tileMatrix)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tilePaint)
                return
            }
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            canvas.drawBitmap(bmp, -bitmapX, -bitmapY, null)
            canvas.restore()
        }
    }

    fun startMovement() {
        if (isMoving || !isAttachedToWindow || isPowerSavingMode) return
        isMoving = true

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val backgroundKey = prefs.getString("animation_style", "background_blocks_large")
        movementSpeed = prefs.getInt("movement_speed", 500).toFloat()

        direction.reset(backgroundKey == "background_stripes" || backgroundKey == "generated_stripes")

        lastFrameTime = 0L

        choreographer.postFrameCallback(movementFrame)
    }

    // Main-thread frame loop: the core function must not depend on animation scale.
    private val movementFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isMoving || !isAttachedToWindow) return
            if (lastFrameTime != 0L) {
                // Avoid a large jump after a stalled frame.
                val delta = ((frameTimeNanos - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
                advanceBitmap(delta)
            }
            lastFrameTime = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

    private fun advanceBitmap(deltaTime: Float) {
        val bmp = bitmap ?: return
        var distance = movementSpeed * deltaTime
        while (distance > 0f) {
            val step = minOf(distance, direction.remaining)
            moveBitmap(bmp, step)
            direction.travel(step)
            distance -= step
        }
        invalidate()
    }

    private fun moveBitmap(bmp: Bitmap, distance: Float) {
        if (tileShader != null) {
            bitmapX = ((bitmapX + direction.x * distance) % bmp.width + bmp.width) % bmp.width
            bitmapY = ((bitmapY + direction.y * distance) % bmp.height + bmp.height) % bmp.height
            return
        }
        val maxX = (bmp.width - viewWidth).toFloat().coerceAtLeast(0f)
        val maxY = (bmp.height - viewHeight).toFloat().coerceAtLeast(0f)
        bitmapX += direction.x * distance
        bitmapY += direction.y * distance
        if (maxX > 0 && (bitmapX >= maxX || bitmapX <= 0f)) direction.reflectX()
        if (maxY > 0 && (bitmapY >= maxY || bitmapY <= 0f)) direction.reflectY()
        bitmapX = bitmapX.coerceIn(0f, maxX)
        bitmapY = bitmapY.coerceIn(0f, maxY)
    }

    fun stopMovement() {
        isMoving = false
        choreographer.removeFrameCallback(movementFrame)
        lastFrameTime = 0L
    }

    fun setPowerSavingMode(enabled: Boolean) {
        if (isPowerSavingMode == enabled) return
        isPowerSavingMode = enabled

        if (enabled) {
            // В режиме энергосбережения движение не нужно
            stopMovement()
        }
        // Фон либо станет черным, либо вернется к обычному — перерисуем
        invalidate()
    }
}
