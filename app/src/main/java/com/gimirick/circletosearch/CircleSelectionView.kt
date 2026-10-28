package com.gimirick.circletosearch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.util.TypedValue
import android.graphics.PointF

class CircleSelectionView(
    context: Context,
    private val screenshot: Bitmap,
    private val onSelection: (Bitmap?) -> Unit,
    private val onCancel: () -> Unit,
) : View(context) {
    private val path = Path()
    private val points = ArrayList<PointF>()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = 0x66000000 }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(7f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(15f)
        alpha = 105
        setShadowLayer(dp(18f), 0f, 0f, Color.WHITE)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(15f)
    }
    private val hintBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC202124.toInt() }
    private var finished = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val colors = intArrayOf(0xFF4285F4.toInt(), 0xFFEA4335.toInt(), 0xFFFBBC05.toInt(), 0xFF34A853.toInt())
        val gradient = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        strokePaint.shader = gradient
        glowPaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(screenshot, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), imagePaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        if (points.isNotEmpty()) {
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, strokePaint)
        }
        val label = "Circle an item  ·  Back to cancel"
        val textWidth = hintPaint.measureText(label)
        val left = (width - textWidth) / 2f
        val top = dp(44f)
        canvas.drawRoundRect(left - dp(16f), top - dp(24f), left + textWidth + dp(16f), top + dp(10f), dp(18f), dp(18f), hintBackground)
        canvas.drawText(label, left, top, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (finished) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                path.reset()
                path.moveTo(event.x, event.y)
                points.add(PointF(event.x, event.y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                addPoint(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                addPoint(event.x, event.y)
                path.lineTo(event.x, event.y)
                finished = true
                val crop = cropSelection()
                onSelection(crop)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                finishCancel()
                return true
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishCancel()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun addPoint(x: Float, y: Float) {
        val previous = points.lastOrNull() ?: return
        val midX = (previous.x + x) / 2f
        val midY = (previous.y + y) / 2f
        path.quadTo(previous.x, previous.y, midX, midY)
        points.add(PointF(x, y))
    }

    private fun cropSelection(): Bitmap? {
        if (points.size < 2 || width <= 0 || height <= 0) return null
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        if (maxX - minX < dp(8f) || maxY - minY < dp(8f)) return null
        val pad = dp(16f)
        val scaleX = screenshot.width.toFloat() / width
        val scaleY = screenshot.height.toFloat() / height
        val left = ((minX - pad).coerceAtLeast(0f) * scaleX).toInt()
        val top = ((minY - pad).coerceAtLeast(0f) * scaleY).toInt()
        val right = ((maxX + pad).coerceAtMost(width.toFloat()) * scaleX).toInt().coerceAtMost(screenshot.width)
        val bottom = ((maxY + pad).coerceAtMost(height.toFloat()) * scaleY).toInt().coerceAtMost(screenshot.height)
        if (right <= left || bottom <= top) return null
        val crop = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
        return if (crop === screenshot) crop.copy(Bitmap.Config.ARGB_8888, false) else crop
    }

    private fun finishCancel() {
        if (finished) return
        finished = true
        onCancel()
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
