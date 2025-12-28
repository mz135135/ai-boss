package com.aiautomation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.aiautomation.R
import com.aiautomation.util.AppLog

/**
 * @deprecated 该服务已废弃，请使用 FloatWindowManager 代替
 * 保留此文件仅为向后兼容
 */
@Deprecated(
    message = "请使用 FloatWindowManager 代替",
    replaceWith = ReplaceWith("FloatWindowManager", "com.aiautomation.service.FloatWindowManager"),
    level = DeprecationLevel.WARNING
)
class FloatingWindowService : Service() {
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isVisible = true
    private var isPaused = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private lateinit var statusText: TextView
    private lateinit var btnStop: ImageButton
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d(TAG, "========== FloatingWindowService.onCreate START ==========")
        AppLog.d(TAG, "FloatingWindowService onCreate")
        
        // 创建通知渠道并启动前台服务
        android.util.Log.d(TAG, "准备创建通知渠道...")
        createNotificationChannel()
        android.util.Log.d(TAG, "准备启动前台服务...")
        startForeground(NOTIF_ID, buildNotification("悬浮窗运行中"))
        android.util.Log.d(TAG, "✓ 前台服务已启动")
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AppLog.e(TAG, "悬浮窗权限未授予，停止服务")
                stopSelf()
                return
            }
        }
        android.util.Log.d(TAG, "✓ 悬浮窗权限已授予，准备创建悬浮窗")
        AppLog.d(TAG, "悬浮窗权限已授予，创建悬浮窗")
        createFloatingWindow()
        android.util.Log.d(TAG, "========== FloatingWindowService.onCreate END ==========" )
        // 保活：保持屏幕常亮与CPU唤醒
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAutomation:runWake").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
    }
    
    private fun createFloatingWindow() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null)
            
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 100
            }
            
            initViews()
            setupListeners()
            setupDragListener()
            
            windowManager?.addView(floatingView, windowParams)
            floatingView?.keepScreenOn = true
            AppLog.d(TAG, "悬浮窗创建成功")
        } catch (e: Exception) {
            AppLog.e(TAG, "创建悬浮窗失败: ${e.message}")
            android.util.Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
        }
    }
    
    private fun initViews() {
        floatingView?.apply {
            statusText = findViewById(R.id.tvStatus)
            btnStop = findViewById(R.id.btnStop)
        }
    }
    
    private fun setupListeners() {
        btnStop.setOnClickListener {
            sendBroadcast(Intent(ACTION_STOP))
            statusText.text = "已停止"
            stopSelf()
        }
    }
    
    private fun setupDragListener() {
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = windowParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (initialTouchX - event.rawX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }
    
    fun updateStatus(status: String) {
        mainHandler.post {
            try {
                if (::statusText.isInitialized) {
                    statusText.text = status
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "更新状态失败: ${e.message}")
            }
        }
    }
    
    fun hideFloatingWindow() {
        mainHandler.post {
            floatingView?.visibility = View.GONE
            isVisible = false
        }
    }
    
    fun showFloatingWindow() {
        mainHandler.post {
            floatingView?.visibility = View.VISIBLE
            isVisible = true
        }
    }
    
    /**
     * 隐藏悬浮窗（截图时调用）
     */
    fun hideWindow() {
        hideFloatingWindow()
    }
    
    /**
     * 显示悬浮窗（截图后调用）
     */
    fun showWindow() {
        showFloatingWindow()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        wakeLock?.release()
        AppLog.d(TAG, "FloatingWindowService onDestroy")
        
        floatingView?.let { windowManager?.removeView(it) }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
                ch.description = "显示任务执行状态的悬浮窗"
                nm.createNotificationChannel(ch)
            }
        }
    }
    
    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("AI 自动化悬浮窗")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }
    
    companion object {
        private const val TAG = "FloatingWindowService"
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIF_ID = 2002
        
        var instance: FloatingWindowService? = null
        
        const val ACTION_UPDATE_STATUS = "com.aiautomation.ACTION_UPDATE_STATUS"
        const val ACTION_PAUSE = "com.aiautomation.ACTION_PAUSE"
        const val ACTION_RESUME = "com.aiautomation.ACTION_RESUME"
        const val ACTION_STOP = "com.aiautomation.ACTION_STOP"
        const val ACTION_HIDE = "com.aiautomation.ACTION_HIDE"
        const val ACTION_SHOW = "com.aiautomation.ACTION_SHOW"
        const val EXTRA_STATUS = "status"
    }
}
