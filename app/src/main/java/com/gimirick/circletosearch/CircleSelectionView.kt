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
import android.os.Build
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class CircleSelectionView(
    context: Context,
    private val screenshot: Bitmap,
    private val onSelection: (Bitmap?) -> Unit,
    private val onCancel: () -> Unit,
) : View(context) {
    private val path = Path()
    private var lastX = 0f
    private var lastY = 0f
    private var minX = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = Float.MIN_VALUE
    private var pointCount = 0
    private var hasPath = false
    private var pillTextLeft = 0f

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
    private val viewBounds = RectF()
    private val pillBounds = RectF()
    private var pillTop = dp(56f)
    private var finished = false

    private val backCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OnBackInvokedCallback { finishCancel() }
    } else null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let {
                findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    it
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let {
                findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it)
            }
        }
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val statusBarInset = insets.getInsets(WindowInsets.Type.statusBars()).top
        val cutoutInset = insets.displayCutout?.safeInsetTop ?: 0
        pillTop = maxOf(statusBarInset, cutoutInset).toFloat() + dp(20f)
        updatePillBounds()
        invalidate()
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val colors = intArrayOf(0xFF4285F4.toInt(), 0xFFEA4335.toInt(), 0xFFFBBC05.toInt(), 0xFF34A853.toInt())
        val gradient = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        strokePaint.shader = gradient
        glowPaint.shader = gradient
        viewBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        updatePillBounds()
    }

    private fun updatePillBounds() {
        val textWidth = hintPaint.measureText(HINT_LABEL)
        pillTextLeft = (width - textWidth) / 2f
        pillBounds.set(pillTextLeft - dp(16f), pillTop - dp(24f), pillTextLeft + textWidth + dp(16f), pillTop + dp(10f))
    }

    override fun onDraw(canvas: Canvas) {
        if (finished || screenshot.isRecycled) return
        super.onDraw(canvas)
        canvas.drawBitmap(screenshot, null, viewBounds, imagePaint)
        canvas.drawRect(viewBounds, dimPaint)
        if (hasPath) {
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, strokePaint)
        }
        canvas.drawRoundRect(pillBounds, dp(18f), dp(18f), hintBackground)
        canvas.drawText(HINT_LABEL, pillTextLeft, pillTop, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (finished) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pillBounds.contains(event.x, event.y)) {
                    finishCancel()
                    return true
                }
                path.reset()
                path.moveTo(event.x, event.y)
                lastX = event.x
                lastY = event.y
                minX = event.x
                maxX = event.x
                minY = event.y
                maxY = event.y
                pointCount = 1
                hasPath = false
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
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishCancel()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun addPoint(x: Float, y: Float) {
        if (pointCount == 0) return
        val midX = (lastX + x) / 2f
        val midY = (lastY + y) / 2f
        path.quadTo(lastX, lastY, midX, midY)
        lastX = x
        lastY = y
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        pointCount++
        hasPath = true
    }

    private fun cropSelection(): Bitmap? {
        if (pointCount < 2 || width <= 0 || height <= 0) return null
        if (maxX - minX < dp(8f) || maxY - minY < dp(8f)) return null
        val pad = dp(16f)
        val scaleX = screenshot.width.toFloat() / width
        val scaleY = screenshot.height.toFloat() / height
        val left = ((minX - pad).coerceAtLeast(0f) * scaleX).toInt()
        val top = ((minY - pad).coerceAtLeast(0f) * scaleY).toInt()
        val right = ((maxX + pad).coerceAtMost(width.toFloat()) * scaleX).toInt().coerceAtMost(screenshot.width)
        val bottom = ((maxY + pad).coerceAtMost(height.toFloat()) * scaleY).toInt().coerceAtMost(screenshot.height)
        if (right <= left || bottom <= top) return null
        return try {
            val crop = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
            if (crop === screenshot) crop.copy(Bitmap.Config.ARGB_8888, false) else crop
        } catch (_: Exception) {
            null
        }
    }

    private fun finishCancel() {
        if (finished) return
        finished = true
        onCancel()
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val HINT_LABEL = "Circle an item  ·  Back to cancel"
    }
}
