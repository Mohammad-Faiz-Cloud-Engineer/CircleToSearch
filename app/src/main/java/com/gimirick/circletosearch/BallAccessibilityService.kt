package com.gimirick.circletosearch

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gimirick.circletosearch.data.SettingsRepository
import com.gimirick.circletosearch.data.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

internal const val BALL_ICON_SCALE = 0.72f

class BallAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val expiryTasks = mutableMapOf<File, Runnable>()
    private lateinit var windowManager: WindowManager
    private lateinit var repository: SettingsRepository
    private lateinit var displayManager: DisplayManager
    private var displayListenerRegistered = false
    private var receiverRegistered = false
    private var settings = SettingsState()
    private var ball: FrameLayout? = null
    private var ballVisual: FrameLayout? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var captureOverlay: CircleSelectionView? = null
    private var captureBitmap: Bitmap? = null
    private var activeCapture = false
    private var captureGeneration = 0
    @Volatile private var lensGeneration = 0
    private var dragX = 0f
    private var dragY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var touchSlop = 0
    private val captureTimeout = Runnable {
        if (activeCapture && captureOverlay == null) {
            finishCapture()
            showToast("Screen capture timed out.")
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            cancelCapture()
            clearLensCache()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                cancelCapture()
                repositionBall()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        repository = SettingsRepository(applicationContext)
        clearLensCache()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        displayListenerRegistered = true
        serviceScope.launch {
            repository.settings.collect { value ->
                settings = value
                if (value.ballEnabled) showBall() else hideBall()
                updateBallSize()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cancelCapture()
        repositionBall()
    }

    override fun onInterrupt() {
        cancelCapture()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onDestroy() {
        cancelCapture()
        hideBall()
        if (receiverRegistered) runCatching { unregisterReceiver(screenOffReceiver) }
        if (displayListenerRegistered) runCatching { displayManager.unregisterDisplayListener(displayListener) }
        clearLensCache()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showBall() {
        if (ball != null) return
        val touchSize = maxOf(dp(settings.ballSizeDp), dp(48))
        val visualSize = dp(settings.ballSizeDp)
        val visual = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), 0x22000000)
            }
            elevation = dp(8).toFloat()
        }
        val iconSize = (visualSize * BALL_ICON_SCALE).toInt()
        visual.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_circle_to_search)
            contentDescription = "Circle to Search"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        ballVisual = visual

        val root = FrameLayout(this).apply {
            addView(visual, FrameLayout.LayoutParams(visualSize, visualSize, Gravity.CENTER))
            setOnTouchListener(::onBallTouch)
        }
        val point = screenSize()
        val params = WindowManager.LayoutParams(
            touchSize,
            touchSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = ((point.x - touchSize) * settings.ballXFraction).toInt()
            y = ((point.y - touchSize) * settings.ballYFraction).toInt()
        }
        clampBall(params)
        windowManager.addView(root, params)
        ball = root
        ballParams = params
    }

    private fun hideBall() {
        ball?.let { runCatching { windowManager.removeView(it) } }
        ball = null
        ballVisual = null
        ballParams = null
    }

    private fun updateBallSize() {
        val root = ball ?: return
        val params = ballParams ?: return
        val size = dp(settings.ballSizeDp)
        val touchSize = maxOf(size, dp(48))
        params.width = touchSize
        params.height = touchSize
        ballVisual?.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        ballVisual?.getChildAt(0)?.layoutParams = FrameLayout.LayoutParams((size * BALL_ICON_SCALE).toInt(), (size * BALL_ICON_SCALE).toInt(), Gravity.CENTER)
        clampBall(params)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun onBallTouch(view: View, event: MotionEvent): Boolean {
        val params = ballParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragX = event.rawX
                dragY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragX
                val dy = event.rawY - dragY
                val slop = if (touchSlop > 0) touchSlop else ViewConfiguration.get(this).scaledTouchSlop
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                if (dragging) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    clampBall(params)
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    snapBall(params)
                    persistBallPosition(params)
                } else onBallTap()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    snapBall(params)
                    persistBallPosition(params)
                }
                return true
            }
        }
        return false
    }

    private fun onBallTap() {
        if (activeCapture) return
        activeCapture = true
        ball?.visibility = View.INVISIBLE
        val generation = ++captureGeneration
        mainHandler.postDelayed(captureTimeout, 5_000)
        mainHandler.post {
            if (generation != captureGeneration) return@post
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val colorSpace = result.colorSpace
                        if (generation != captureGeneration) {
                            buffer.close()
                            return
                        }
                        serviceScope.launch(Dispatchers.Default) {
                            val bitmap = try {
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                                try {
                                    wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                } finally {
                                    wrapped?.recycle()
                                }
                            } finally {
                                buffer.close()
                            }
                            val blank = bitmap == null || isBlank(bitmap)
                            withContext(Dispatchers.Main) {
                                if (generation != captureGeneration) {
                                    bitmap?.recycle()
                                    return@withContext
                                }
                                if (blank) {
                                    bitmap?.recycle()
                                    finishCapture()
                                    showToast("This screen blocks screenshots.")
                                    return@withContext
                                }
                                showSelection(bitmap, generation)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (generation != captureGeneration) return
                        finishCapture()
                        showToast("Screen capture unavailable. This app may block screenshots.")
                    }
                })
            } catch (_: RuntimeException) {
                if (generation == captureGeneration) {
                    finishCapture()
                    showToast("Screen capture unavailable. Try again in a moment.")
                }
            }
        }
    }

    private fun showSelection(bitmap: Bitmap, generation: Int) {
        if (generation != captureGeneration) {
            bitmap.recycle()
            return
        }
        mainHandler.removeCallbacks(captureTimeout)
        captureBitmap = bitmap
        val selection = CircleSelectionView(
            this,
            bitmap,
            onSelection = { crop ->
                if (generation == captureGeneration) {
                    finishCapture()
                    if (crop != null) handoffCrop(crop)
                } else crop?.recycle()
            },
            onCancel = { if (generation == captureGeneration) cancelCapture() },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }
        try {
            windowManager.addView(selection, params)
            captureOverlay = selection
        } catch (_: RuntimeException) {
            bitmap.recycle()
            captureBitmap = null
            finishCapture()
            showToast("Could not open the selection overlay.")
        }
    }

    private fun finishCapture() {
        mainHandler.removeCallbacks(captureTimeout)
        captureOverlay?.let { runCatching { windowManager.removeView(it) } }
        captureOverlay = null
        captureBitmap?.recycle()
        captureBitmap = null
        activeCapture = false
        captureGeneration++
        if (settings.ballEnabled) {
            ball?.visibility = View.VISIBLE
        }
    }

    private fun cancelCapture() {
        finishCapture()
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 8 && maxOf(Color.red(color), Color.green(color), Color.blue(color)) > 8) return false
            }
        }
        return true
    }

    private fun repositionBall() {
        val params = ballParams ?: return
        val point = screenSize()
        params.x = (settings.ballXFraction * (point.x - params.width)).toInt()
        params.y = (settings.ballYFraction * (point.y - params.height)).toInt()
        clampBall(params)
        ball?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun snapBall(params: WindowManager.LayoutParams) {
        val point = screenSize()
        val maxX = (point.x - params.width).coerceAtLeast(0)
        val maxY = (point.y - params.height).coerceAtLeast(0)
        val distances = intArrayOf(params.x, maxX - params.x, params.y, maxY - params.y)
        when (distances.indices.minByOrNull { distances[it] }) {
            0 -> params.x = 0
            1 -> params.x = maxX
            2 -> params.y = 0
            else -> params.y = maxY
        }
        clampBall(params)
        ball?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun persistBallPosition(params: WindowManager.LayoutParams) {
        val point = screenSize()
        val x = params.x.toFloat() / (point.x - params.width).coerceAtLeast(1)
        val y = params.y.toFloat() / (point.y - params.height).coerceAtLeast(1)
        serviceScope.launch { repository.setBallPosition(x, y) }
    }

    private fun clampBall(params: WindowManager.LayoutParams) {
        val point = screenSize()
        params.x = params.x.coerceIn(0, (point.x - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (point.y - params.height).coerceAtLeast(0))
    }

    private fun screenSize(): Point {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }

    private fun clearLensCache() {
        lensGeneration++
        expiryTasks.forEach { (file, task) ->
            mainHandler.removeCallbacks(task)
            file.delete()
        }
        expiryTasks.clear()
        val folder = File(cacheDir, "lens")
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }
    }

    private fun handoffCrop(crop: Bitmap) {
        val generation = lensGeneration
        val job = serviceScope.launch(Dispatchers.IO) {
            var file: File? = null
            try {
                val folder = File(cacheDir, "lens").apply { mkdirs() }
                file = File(folder, "crop-${System.currentTimeMillis()}-${System.nanoTime()}.png")
                FileOutputStream(file).use { output ->
                    if (!crop.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Could not encode crop")
                }
                withContext(Dispatchers.Main) {
                    if (generation != lensGeneration) file.delete()
                    else openLens(file)
                }
            } catch (_: Exception) {
                file?.delete()
                withContext(Dispatchers.Main) {
                    if (generation == lensGeneration) showToast("Could not prepare the selected image.")
                }
            } finally {
                crop.recycle()
            }
        }
        // Fallback recycle in case coroutine was cancelled before execution started
        job.invokeOnCompletion { if (!crop.isRecycled) crop.recycle() }
    }

    private fun openLens(file: File) {
        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            file.delete()
            showToast("Could not share the selected image.")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "Selected image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val installed = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(GOOGLE_APP_PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(GOOGLE_APP_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val target = Intent(send).setPackage(GOOGLE_APP_PACKAGE)
        try {
            val resolvable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(target, PackageManager.ResolveInfoFlags.of(0)) != null
            } else {
                @Suppress("DEPRECATION")
                target.resolveActivity(packageManager) != null
            }
            if (installed && resolvable) {
                target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(target)
            } else {
                if (!installed) showToast("Google app is not installed. Choose another app to search this image.")
                val chooser = Intent.createChooser(send, "Search this image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(chooser)
            }
            scheduleDelete(file)
        } catch (_: RuntimeException) {
            file.delete()
            showToast("No app could open the selected image.")
        }
    }

    private fun scheduleDelete(file: File) {
        val task = Runnable {
            file.delete()
            expiryTasks.remove(file)
        }
        expiryTasks[file] = task
        mainHandler.postDelayed(task, 60_000)
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
