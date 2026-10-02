package com.divinegames.mmover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One static tile, owned by the attached preference row; never caches all styles. */
class PatternPreviewView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint()
    private var bitmap: Bitmap? = null
    private var job: Job? = null
    private var request = 0
    private var key: String? = null
    private var loadedKey: String? = null

    init { clipToOutline = true }

    fun showPattern(value: String?, label: CharSequence?) {
        contentDescription = context.getString(R.string.background_preview_description, label ?: "")
        if (key == value && (loadedKey == value || job?.isActive == true)) return
        key = value
        load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        load()
    }

    private fun load() {
        if (!isAttachedToWindow) return
        val owner = findViewTreeLifecycleOwner() ?: return
        val selected = key ?: return
        if (selected == loadedKey) return
        job?.cancel()
        val generation = ++request
        releaseBitmap()
        invalidate()
        if (selected !in ProceduralBackground.keys) return
        job = owner.lifecycleScope.launch {
            val pixels = withContext(Dispatchers.Default) { ProceduralBackground.generate(selected) }
            if (!isAttachedToWindow || generation != request) return@launch
            val tile = Bitmap.createBitmap(pixels, ProceduralBackground.SIZE,
                ProceduralBackground.SIZE, Bitmap.Config.ARGB_8888)
            bitmap = tile
            paint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            loadedKey = selected
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bitmap != null) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun releaseBitmap() {
        paint.shader = null
        bitmap?.recycle()
        bitmap = null
        loadedKey = null
    }

    override fun onDetachedFromWindow() {
        ++request
        job?.cancel()
        job = null
        releaseBitmap()
        super.onDetachedFromWindow()
    }
}
