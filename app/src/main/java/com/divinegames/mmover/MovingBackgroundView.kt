// MovingBackgroundView.kt — обновлённый код с колбэком после загрузки
package com.divinegames.mmover

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.view.doOnLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MovingBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    @Volatile
    private var bitmap: Bitmap? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var bitmapX = 0f
    private var bitmapY = 0f

    private var animator: ValueAnimator? = null
    private var isMoving = false

    private var horizontalDirection = 1f
    private var verticalDirection = 1f
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
    /**
     * Асинхронно загружает и подготавливает фон.
     * Теперь он проверяет, нужно ли вообще что-то грузить.
     */
    fun loadBackgroundWithCallback(onLoaded: () -> Unit) {
        hasLoadBeenTriggered = true

        doOnLayout {
            val currentViewWidth = this.width
            val currentViewHeight = this.height
            if (currentViewWidth == 0 || currentViewHeight == 0) {
                Log.e("BG_LOAD", "Критическая ошибка: Ширина или высота View равна 0.")
                return@doOnLayout
            }

            findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val newBackgroundKey = prefs.getString("animation_style", "background_blocks_medium")

                if (newBackgroundKey == currentBackgroundKey && bitmap != null) {
                    Log.d("BG_LOAD", "Фон ($newBackgroundKey) уже загружен. Пропускаем.")
                    withContext(Dispatchers.Main) { onLoaded() }
                    return@launch
                }

                Log.d("BG_LOAD", "Загружаю новый фон: $newBackgroundKey")

                val loadedBitmap = withContext(Dispatchers.IO) {
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

                        // --- НОВАЯ, ПРАВИЛЬНАЯ ФУНКЦИЯ ---
                        options.inSampleSize = calculateInSampleSize(options, currentViewWidth, currentViewHeight)
                        Log.d("BG_LOAD", "Установлен inSampleSize: ${options.inSampleSize}")

                        options.inPreferredConfig = Bitmap.Config.RGB_565
                        options.inJustDecodeBounds = false

                        val smallBitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
                            ?: return@withContext createErrorBitmap()

                        Log.d("BG_LOAD", "Размер после inSampleSize: ${smallBitmap.width}x${smallBitmap.height}")

                        // --- ФИНАЛЬНОЕ ИСПРАВЛЕНИЕ ДЛЯ STRIPES ---
                        if (newBackgroundKey == "background_stripes") {
                            // 1. Вычисляем новую высоту на основе ширины экрана
                            val scaledHeight = smallBitmap.height

                            Log.d("BG_LOAD", "Масштабируем Stripes до ${currentViewWidth}x${scaledHeight}")

                            // 2. Создаем финальный битмап
                            val scaledBitmap = Bitmap.createScaledBitmap(smallBitmap, currentViewWidth, scaledHeight, true)
                            if (scaledBitmap != smallBitmap) {
                                smallBitmap.recycle()
                            }
                            scaledBitmap
                        } else {
                            // Для "Blocks" просто возвращаем сжатую картинку
                            smallBitmap
                        }
                    } catch (e: Exception) {
                        Log.e("BG_LOAD", "Ошибка загрузки изображения: ${e.message}", e)
                        createErrorBitmap()
                    }
                }

                bitmap?.recycle()
                bitmap = loadedBitmap
                currentBackgroundKey = newBackgroundKey
                bitmapX = 0f
                bitmapY = 0f

                val sizeMB = bitmap?.byteCount?.toFloat()?.div(1024 * 1024) ?: 0f
                Log.d("BG_LOAD", "Финальный размер: ${bitmap?.width}x${bitmap?.height}, занимает: ${String.format("%.2f", sizeMB)} MB")

                invalidate()

                withContext(Dispatchers.Main) {
                    onLoaded()
                }
            }
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
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            canvas.drawBitmap(bmp, -bitmapX, -bitmapY, null)
            canvas.restore()
        }
    }

    fun startMovement() {
        if (isMoving) return
        isMoving = true

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val backgroundKey = prefs.getString("animation_style", "background_blocks_large")
        val speed = prefs.getInt("movement_speed", 300).toFloat()

        when (backgroundKey) {
            "background_stripes" -> {
                horizontalDirection = 0f
                verticalDirection = if (Random.nextBoolean()) 1f else -1f
            }
            else -> {
                val randomAngle = Random.nextDouble() * 2 * Math.PI
                horizontalDirection = Math.cos(randomAngle).toFloat()
                verticalDirection = Math.sin(randomAngle).toFloat()
            }
        }

        lastFrameTime = 0L

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
            duration = 1000

            addUpdateListener {
                val currentTime = System.nanoTime()
                if (lastFrameTime == 0L) {
                    lastFrameTime = currentTime
                    return@addUpdateListener
                }
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f
                lastFrameTime = currentTime

                val bmp = bitmap ?: return@addUpdateListener

                val maxX = (bmp.width - viewWidth).toFloat().coerceAtLeast(0f)
                val maxY = (bmp.height - viewHeight).toFloat().coerceAtLeast(0f)

                bitmapX += horizontalDirection * speed * deltaTime
                bitmapY += verticalDirection * speed * deltaTime

                if (maxX > 0 && (bitmapX >= maxX || bitmapX <= 0f)) {
                    horizontalDirection *= -1
                }
                if (maxY > 0 && (bitmapY >= maxY || bitmapY <= 0f)) {
                    verticalDirection *= -1
                }

                bitmapX = bitmapX.coerceIn(0f, maxX)
                bitmapY = bitmapY.coerceIn(0f, maxY)

                invalidate()
            }
        }
        animator?.start()
    }

    fun stopMovement() {
        isMoving = false
        animator?.cancel()
        animator = null
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