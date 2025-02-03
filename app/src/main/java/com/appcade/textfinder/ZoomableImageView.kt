package com.appcade.textfinder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private var scaleFactor = 1f
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f

    init {
        scaleType = ScaleType.MATRIX
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                startX = lastX
                startY = lastY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY

                matrix.postTranslate(dx, dy)
                imageMatrix = matrix

                lastX = event.x
                lastY = event.y
            }
        }
        return true
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post {
            resetZoom()
        }
    }

    fun resetZoom() {
        if (drawable == null) return

        // Získání rozměrů obrázku a view
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Výpočet měřítka pro přizpůsobení na šířku nebo výšku
        val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)

        // Výpočet posunu pro centrování
        val dx = (viewWidth - drawableWidth * scale) / 2
        val dy = (viewHeight - drawableHeight * scale) / 2

        // Nastavení matice
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        // Aplikace matice na ImageView
        imageMatrix = matrix
        scaleFactor = 1f
    }

    private inner class ScaleListener : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            scaleFactor *= scale
            scaleFactor = max(1f, min(scaleFactor, 5f))

            matrix.postScale(scale, scale, detector.focusX, detector.focusY)
            imageMatrix = matrix
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
        override fun onScaleEnd(detector: ScaleGestureDetector) {}
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetZoom()
            return true
        }
    }

}
