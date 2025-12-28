package com.aiautomation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aiautomation.ai.DoubaoApiClient
import com.aiautomation.automation.TaskManager
import com.aiautomation.data.model.Task
import com.aiautomation.data.model.TaskStatus
import com.aiautomation.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutomationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentJob: Job? = null
    private var currentTaskManager: TaskManager? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                AppLog.d(TAG, "收到停止指令，正在中断任务")
                try { currentTaskManager?.stop() } catch (_: Exception) {}
                try { currentJob?.cancel() } catch (_: Exception) {}
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("正在执行自动化任务"))
        com.aiautomation.util.AppCtx.init(applicationContext)

        // 监听停止广播（悬浮窗按钮/快捷键）
        val filter = IntentFilter(ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stopReceiver, filter)
        }

        AppLog.d(TAG, "AutomationService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskText = intent?.getStringExtra(EXTRA_TASK_TEXT)
        val conversationId = intent?.getLongExtra(EXTRA_CONVERSATION_ID, -1L) ?: -1L
        if (taskText.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        currentJob = scope.launch {
            try {
                AppLog.d(TAG, "开始后台执行任务: $taskText")
                val api = DoubaoApiClient.getInstance()
                val tm = TaskManager(applicationContext, api, DoubaoApiClient.DEFAULT_MODEL_ID)
                currentTaskManager = tm
                
                // 连接悬浮窗停止回调
                FloatWindowManager.onStopRequested = {
                    AppLog.d(TAG, "悬浮窗请求停止任务")
                    tm.stop()
                    currentJob?.cancel()
                }
                
                val result = tm.executeTask(Task(title = "自动化任务", description = taskText))
                AppLog.d(TAG, "任务结束: $result")
                // 发送结果广播，便于 ChatActivity 追加消息
                val br = Intent(ACTION_AUTOMATION_RESULT).apply {
                    putExtra(EXTRA_CONVERSATION_ID, conversationId)
                    putExtra(EXTRA_RESULT_STATUS, result.name)
                }
                sendBroadcast(br)
            } catch (e: Exception) {
                AppLog.e(TAG, "后台任务异常: ${e.message}")
            } finally {
                currentTaskManager = null
                currentJob = null
                // 清理回调
                FloatWindowManager.onStopRequested = null
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        scope.cancel()
        AppLog.d(TAG, "AutomationService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "自动化执行", NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AI 自动化")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AutomationService"
        private const val CHANNEL_ID = "automation_running"
        private const val NOTIF_ID = 2001

        const val ACTION_AUTOMATION_RESULT = "com.aiautomation.ACTION_AUTOMATION_RESULT"
        const val ACTION_STOP = "com.aiautomation.ACTION_STOP"
        const val EXTRA_TASK_TEXT = "task_text"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_RESULT_STATUS = "result_status"
    }
}