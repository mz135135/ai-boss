package com.aiautomation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var imageThread: HandlerThread? = null
    private var imageHandler: Handler? = null
    private val lastBase64 = AtomicReference<String?>(null)
    private val lastFrameId = AtomicLong(0)
    private val lastFrameTs = AtomicLong(0)
    private val lastJpeg = AtomicReference<ByteArray?>(null)
    private val frameLock = Object()
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ScreenCapture"
        
        var instance: ScreenCaptureService? = null
        
        const val ACTION_START = "ACTION_START"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d(TAG, "ScreenCaptureService onCreate")
        
        // 获取屏幕尺寸
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
            screenHeight = windowMetrics.bounds.height()
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "========== onStartCommand START ==========")
        android.util.Log.d(TAG, "intent: $intent")
        android.util.Log.d(TAG, "action: ${intent?.action}")
        android.util.Log.d(TAG, "flags: $flags, startId: $startId")
        
        if (intent?.action == ACTION_START) {
            android.util.Log.d(TAG, "Action matched: ACTION_START")
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            }
            
            android.util.Log.d(TAG, "resultCode=$resultCode")
            android.util.Log.d(TAG, "data=$data")
            
            if (data != null) {
                android.util.Log.d(TAG, "Calling startCapture...")
                startCapture(resultCode, data)
            } else {
                android.util.Log.e(TAG, "无效的 resultCode 或 data")
            }
        } else {
            android.util.Log.w(TAG, "Action not matched. Expected: $ACTION_START, Got: ${intent?.action}")
        }
        
        android.util.Log.d(TAG, "========== onStartCommand END ==========")
        return START_STICKY
    }
    
    private fun startCapture(resultCode: Int, data: Intent) {
        android.util.Log.d(TAG, "========== startCapture START ==========")
        android.util.Log.d(TAG, "startCapture: 屏幕尺寸=${screenWidth}x${screenHeight}, dpi=$screenDensity")
        android.util.Log.d(TAG, "resultCode=$resultCode")
        
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            android.util.Log.d(TAG, "MediaProjectionManager 获取成功")
            
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            android.util.Log.d(TAG, "MediaProjection 创建成功: $mediaProjection")
            
            if (mediaProjection == null) {
                android.util.Log.e(TAG, "MediaProjection 为 null！")
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    android.util.Log.w(TAG, "MediaProjection onStop 回调，释放资源")
                    stopCapture()
                }
            }, null)
            
            // 创建 ImageReader（增加队列深度以提高容错）
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                5
            )
            android.util.Log.d(TAG, "ImageReader 创建成功: $imageReader, surface=${imageReader?.surface}")

            // 启动图像线程并注册监听
            imageThread = HandlerThread("SC_Image").apply { start() }
            imageHandler = Handler(imageThread!!.looper)
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bmp = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    image.close()

                    val cropped = if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
                    val baos = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val bytes = baos.toByteArray()
                    lastJpeg.set(bytes)
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    lastBase64.set(base64)
                    lastFrameId.incrementAndGet()
                    lastFrameTs.set(System.currentTimeMillis())
                    synchronized(frameLock) { frameLock.notifyAll() }
                    if (cropped !== bmp) bmp.recycle()
                    cropped.recycle()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "onImageAvailable 失败", e)
                }
            }, imageHandler)
            
            // 释放已有的虚拟屏幕
            virtualDisplay?.release()

            // 创建虚拟屏幕
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            android.util.Log.d(TAG, "VirtualDisplay 创建成功: $virtualDisplay")
            
            if (virtualDisplay == null) {
                android.util.Log.e(TAG, "VirtualDisplay 为 null！")
                return
            }
            
            // 等待第一帧准备好，防止立即截图时没有数据
            android.util.Log.d(TAG, "等待虚拟屏幕生产第一帧...")
            Thread.sleep(1000)
            
            android.util.Log.d(TAG, "✓ 屏幕录制已就绪！lastFrameId=${lastFrameId.get()}")
            android.util.Log.d(TAG, "========== startCapture END ==========")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "startCapture 失败: ${e.javaClass.simpleName} message=${e.message}")
            android.util.Log.e(TAG, "Exception: ${e.stackTraceToString()}")
        }
    }

    private fun stopCapture() {
        android.util.Log.d(TAG, "stopCapture: 释放 VirtualDisplay / ImageReader / MediaProjection")
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }
    
    /**
     * 截取当前屏幕 - 从缓存还原，避免与监听器竞争
     * @return Bitmap 或 null
     */
    fun captureScreen(): Bitmap? {
        return try {
            val bytes = lastJpeg.get()
            if (bytes == null) {
                android.util.Log.w(TAG, "captureScreen: lastJpeg 为 null，没有可用帧")
                return null
            }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            android.util.Log.d(TAG, "captureScreen 成功: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            android.util.Log.e(TAG, "captureScreen 失败", e)
            null
        }
    }
    
    // 记录连续复用旧帧的次数
    private var consecutiveReusedFrames = 0
    private val maxReusedFrames = 2  // 连续2次复用就重启
    
    /**
     * 截图并转为 Base64（用于发送给豆包）
     */
    fun captureScreenAsBase64(quality: Int = 80): String? {
        android.util.Log.d(TAG, "captureScreenAsBase64 开始")
        val startId = lastFrameId.get()
        
        // 1) 先等待新帧
        val newFrame = waitForFrameAfter(startId, 1000)
        if (newFrame != null) {
            android.util.Log.d(TAG, "captureScreenAsBase64 成功: 获取到新帧 (frameId=${lastFrameId.get()})")
            consecutiveReusedFrames = 0  // 重置计数
            return newFrame
        }
        
        // 2) 没等到新帧，记录复用次数
        consecutiveReusedFrames++
        android.util.Log.w(TAG, "⚠️ captureScreenAsBase64: 未收到新帧 (第${consecutiveReusedFrames}次) frameId=$startId")
        
        // 3) 连续复用过多，尝试重启VirtualDisplay
        if (consecutiveReusedFrames >= maxReusedFrames) {
            android.util.Log.e(TAG, "🔄 连续${consecutiveReusedFrames}次未收到新帧，尝试重启VirtualDisplay")
            restartVirtualDisplay()
            consecutiveReusedFrames = 0
            
            // 重启后再等待一次
            val restartedFrame = waitForFrameAfter(startId, 1000)
            if (restartedFrame != null) {
                android.util.Log.d(TAG, "✅ 重启后成功获取新帧")
                return restartedFrame
            }
        }
        
        // 4) 若有旧帧则复用（但已经记录了复用次数）
        lastBase64.get()?.let {
            android.util.Log.w(TAG, "captureScreenAsBase64: 复用上一帧 (frameId=$startId)")
            return it
        }
        
        // 5) 既没新帧也没旧帧 => 踢一下 VirtualDisplay 再等一小会
        android.util.Log.w(TAG, "captureScreenAsBase64: 无可用帧，尝试重绑 Surface")
        kickVirtualDisplay()
        val kickedFrame = waitForFrameAfter(startId, 500)
        if (kickedFrame != null) {
            android.util.Log.d(TAG, "captureScreenAsBase64 成功: 重绑后获取到新帧")
            return kickedFrame
        }
        
        android.util.Log.e(TAG, "❌ captureScreenAsBase64 失败: 无可用帧")
        return null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕录制服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI 自动化屏幕录制"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI 自动化")
            .setContentText("屏幕录制中...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun waitForFrameAfter(startId: Long, timeoutMs: Long): String? {
        val start = System.currentTimeMillis()
        synchronized(frameLock) {
            while (System.currentTimeMillis() - start < timeoutMs) {
                val id = lastFrameId.get()
                val b64 = lastBase64.get()
                if (id > startId && b64 != null) {
                    return b64
                }
                val remaining = timeoutMs - (System.currentTimeMillis() - start)
                if (remaining <= 0) break
                try { frameLock.wait(remaining) } catch (_: InterruptedException) {}
            }
        }
        return null
    }
    
    /**
     * 轻量唤醒 VirtualDisplay：解绑再重绑 Surface
     */
    private fun kickVirtualDisplay() {
        try {
            val vd = virtualDisplay ?: return
            val surf = imageReader?.surface ?: return
            vd.setSurface(null)
            Thread.sleep(50)
            vd.setSurface(surf)
            android.util.Log.w(TAG, "kickVirtualDisplay: Surface 重绑完成")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "kickVirtualDisplay 失败", e)
        }
    }
    
    /**
     * 重启VirtualDisplay（当连续获取不到新帧时）
     */
    private fun restartVirtualDisplay() {
        try {
            android.util.Log.w(TAG, "restartVirtualDisplay: 开始重启...")
            
            // 1. 释放旧的VirtualDisplay
            virtualDisplay?.release()
            virtualDisplay = null
            Thread.sleep(100)
            
            // 2. 重新创建VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            
            if (virtualDisplay != null) {
                android.util.Log.d(TAG, "✅ restartVirtualDisplay: 重启成功")
                Thread.sleep(200)  // 等待新VirtualDisplay就绪
            } else {
                android.util.Log.e(TAG, "❌ restartVirtualDisplay: 重启失败，virtualDisplay为null")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "restartVirtualDisplay 异常", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopCapture()
        mediaProjection?.stop()
        imageThread?.quitSafely(); imageThread = null; imageHandler = null
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
