package com.aiautomation.ui

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import android.media.projection.MediaProjectionManager
import com.aiautomation.service.ScreenCaptureService
import com.aiautomation.data.model.Task
import com.aiautomation.data.model.TaskStatus
import com.aiautomation.util.PermissionUtils
import com.aiautomation.util.ExecPrefs
import com.aiautomation.util.AppSettings
import com.aiautomation.automation.TaskManager
import android.media.MediaPlayer
import com.aiautomation.ai.DoubaoApiClient
import com.aiautomation.service.AutomationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.aiautomation.ui.screens.HomeScreen
import com.aiautomation.ui.screens.UserScreen
import android.provider.Settings
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.aiautomation.voice.VoiceRecognizer
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

class NewMainActivity : ComponentActivity() {
    private lateinit var taskManager: TaskManager
    private val apiClient by lazy { DoubaoApiClient() }
    private lateinit var sharedPrefs: SharedPreferences
    
    // Vosk 语音识别
    private var voiceRecognizer: VoiceRecognizer? = null
    private var isVoiceModelInitialized = false
    private var accumulatedVoiceText = ""
    private var voiceResultCallback: ((String) -> Unit)? = null
    
    companion object {
        private const val CHANNEL_ID = "boss_helper_tasks"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_TASK_HISTORY = "task_history"
        private const val PREFS_CHAT_HISTORY = "chat_history"
        private const val REQUEST_RECORD_AUDIO = 1002
    }
    
    // 屏幕录制权限launcher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // 启动屏幕录制服务
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
            
            // 等待服务启动后执行任务
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                pendingTaskCallback?.invoke()
                pendingTaskCallback = null
            }, 1000)
        } else {
            android.widget.Toast.makeText(this, "需要屏幕录制权限才能执行任务", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private var voiceInputCallback: ((String) -> Unit)? = null
    private var pendingTaskCallback: (() -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化
        sharedPrefs = getSharedPreferences("boss_helper_prefs", Context.MODE_PRIVATE)
        val modelId = ExecPrefs.getModelId(this) ?: DoubaoApiClient.DEFAULT_MODEL_ID
        taskManager = TaskManager(this, apiClient, modelId)
        
        // 初始化Vosk语音识别
        initVoiceRecognizer()
        
        // 创建通知通道
        createNotificationChannel()
        
        setContent {
            AutoMateTheme {
                MainScreen(
                    taskManager = taskManager,
                    onNavigateToOldUI = {
                        startActivity(android.content.Intent(this, MainActivity::class.java))
                    },
                    onVoiceInput = { callback ->
                        voiceInputCallback = callback
                        startVoiceRecognition()
                    },
                    onVoiceStart = { callback ->
                        voiceResultCallback = callback
                        startVoiceRecording()
                    },
                    onVoiceStop = {
                        stopVoiceRecording()
                    },
                    onOpenSettings = { action ->
                        when (action) {
                            "accessibility" -> {
                                startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                            "overlay" -> {
                                startActivity(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                            }
                            "logs" -> {
                                startActivity(android.content.Intent(this, ExecRecordsActivity::class.java))
                            }
                        }
                    },
                    checkScreenCapturePermission = ::checkAndRequestScreenCapture,
                    saveTaskToHistory = ::saveTaskToHistory,
                    loadChatHistory = ::loadChatHistory,
                    saveChatHistory = ::saveChatHistory,
                    clearChatHistory = {
                        sharedPrefs.edit().remove(PREFS_CHAT_HISTORY).apply()
                    }
                )
            }
        }
    }
    
    private fun startVoiceRecognition() {
        // 兼容旧接口 - 3秒自动停止
        if (!isVoiceModelInitialized) {
            android.widget.Toast.makeText(this, "语音识别正在初始化，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!checkAudioPermission()) {
            return
        }
        
        startVoiceRecording()
        
        // 3秒后自动停止
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopVoiceRecording()
        }, 3000)
        
        android.widget.Toast.makeText(this, "请说话...", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 开始语音录制
     */
    private fun startVoiceRecording() {
        if (!isVoiceModelInitialized) {
            android.widget.Toast.makeText(this, "语音识别正在初始化，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!checkAudioPermission()) {
            return
        }
        
        accumulatedVoiceText = ""
        
        voiceRecognizer?.apply {
            onPartialResult = { partial ->
                // 实时显示
                android.util.Log.d("VoiceInput", "实时: $partial")
            }
            
            onResult = { text ->
                // 累积识别结果
                accumulatedVoiceText = if (accumulatedVoiceText.isEmpty()) text else "$accumulatedVoiceText $text"
                android.util.Log.d("VoiceInput", "结果: $text, 累积: $accumulatedVoiceText")
            }
            
            onError = { error ->
                android.util.Log.e("VoiceInput", "错误: $error")
                android.widget.Toast.makeText(this@NewMainActivity, error, android.widget.Toast.LENGTH_SHORT).show()
            }
            
            startListening()
            android.util.Log.d("VoiceInput", "开始语音录制")
        }
    }
    
    /**
     * 停止语音录制
     */
    private fun stopVoiceRecording() {
        voiceRecognizer?.apply {
            stopListening()
            android.util.Log.d("VoiceInput", "停止语音录制")
            
            // 返回结果
            if (accumulatedVoiceText.isNotEmpty()) {
                // 去除空格（Vosk中文模型会在词间加空格）
                val cleanText = accumulatedVoiceText.replace(" ", "")
                android.util.Log.d("VoiceInput", "清理后: $cleanText")
                
                // 使用按住录音的回调，或者使用兼容回调
                voiceResultCallback?.invoke(cleanText) ?: voiceInputCallback?.invoke(cleanText)
                
                accumulatedVoiceText = ""
                voiceResultCallback = null
            }
        }
    }
    
    private fun initVoiceRecognizer() {
        voiceRecognizer = VoiceRecognizer(this)
        
        // 异步加载模型
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("VoiceInput", "开始加载Vosk模型...")
                val success = voiceRecognizer?.initModel() ?: false
                isVoiceModelInitialized = success
                
                if (success) {
                    android.util.Log.d("VoiceInput", "Vosk模型加载成功")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@NewMainActivity, "语音识别已就绪", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.util.Log.e("VoiceInput", "Vosk模型加载失败")
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceInput", "初始化语音识别失败", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@NewMainActivity, "语音识别初始化失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun checkAudioPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            false
        } else {
            true
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(this, "录音权限已授予", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "需要录音权限才能使用语音输入", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceRecognizer?.release()
    }
    
    private fun checkAndRequestScreenCapture(onGranted: () -> Unit) {
        // 检查屏幕录制服务是否已启动
        if (ScreenCaptureService.instance != null) {
            onGranted()
            return
        }
        
        // 请求屏幕录制权限
        pendingTaskCallback = onGranted
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Boss助手任务通知"
            val descriptionText = "任务执行完成通知"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun saveTaskToHistory(taskDesc: String) {
        try {
            val historyJson = sharedPrefs.getString(PREFS_TASK_HISTORY, "[]")
            val historyArray = JSONArray(historyJson)
            
            // 检查是否已存在
            var exists = false
            for (i in 0 until historyArray.length()) {
                if (historyArray.getJSONObject(i).getString("task") == taskDesc) {
                    exists = true
                    // 更新时间
                    historyArray.getJSONObject(i).put("timestamp", System.currentTimeMillis())
                    historyArray.getJSONObject(i).put("count", 
                        historyArray.getJSONObject(i).getInt("count") + 1
                    )
                    break
                }
            }
            
            if (!exists) {
                val taskObj = JSONObject()
                taskObj.put("task", taskDesc)
                taskObj.put("timestamp", System.currentTimeMillis())
                taskObj.put("count", 1)
                historyArray.put(taskObj)
            }
            
            // 保留最近20条
            if (historyArray.length() > 20) {
                // 删除最旧的
                val newArray = JSONArray()
                for (i in 1 until historyArray.length()) {
                    newArray.put(historyArray.getJSONObject(i))
                }
                sharedPrefs.edit().putString(PREFS_TASK_HISTORY, newArray.toString()).apply()
            } else {
                sharedPrefs.edit().putString(PREFS_TASK_HISTORY, historyArray.toString()).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getTaskHistory(): List<String> {
        return try {
            val historyJson = sharedPrefs.getString(PREFS_TASK_HISTORY, "[]")
            val historyArray = JSONArray(historyJson)
            val tasks = mutableListOf<String>()
            for (i in 0 until historyArray.length()) {
                tasks.add(historyArray.getJSONObject(i).getString("task"))
            }
            tasks.reversed() // 最近的在前
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveChatHistory(messages: List<ChatMessage>) {
        try {
            val jsonArray = JSONArray()
            messages.forEach { msg ->
                val jsonObj = JSONObject()
                jsonObj.put("id", msg.id)
                jsonObj.put("isAI", msg.isAI)
                jsonObj.put("text", msg.text)
                jsonObj.put("time", msg.time)
                jsonObj.put("isThinking", msg.isThinking)
                jsonArray.put(jsonObj)
            }
            sharedPrefs.edit().putString(PREFS_CHAT_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadChatHistory(): List<ChatMessage> {
        return try {
            val historyJson = sharedPrefs.getString(PREFS_CHAT_HISTORY, "[]")
            val historyArray = JSONArray(historyJson)
            val messages = mutableListOf<ChatMessage>()
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.getJSONObject(i)
                messages.add(
                    ChatMessage(
                        id = obj.getLong("id"),
                        isAI = obj.getBoolean("isAI"),
                        text = obj.getString("text"),
                        time = obj.getString("time"),
                        isThinking = obj.optBoolean("isThinking", false)
                    )
                )
            }
            messages
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

private fun sendNotification(activity: android.app.Activity, taskDesc: String) {
    val intent = android.content.Intent(activity, NewMainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        activity, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val builder = NotificationCompat.Builder(activity, "boss_helper_tasks")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Boss助手")
        .setContentText("任务执行完成: ${taskDesc.take(30)}")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
    
    val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(1001, builder.build())
}

private fun playCompletionSound(context: Context) {
    try {
        val mediaPlayer = MediaPlayer.create(context, android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_NOTIFICATION
        ))
        mediaPlayer?.setOnCompletionListener { mp ->
            mp.release()
        }
        mediaPlayer?.start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun AutoMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6366F1), // Indigo
            secondary = Color(0xFF22D3EE), // Cyan
            background = Color(0xFF0B1120), // Deep blue-black
            surface = Color(0xFF151E32),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFFF1F5F9), // Slate-100
            onSurface = Color(0xFFCBD5E1) // Slate-300
        ),
        content = content
    )
}

enum class MainTab {
    HOME, USER
}

data class ChatMessage(
    val id: Long,
    val isAI: Boolean,
    val text: String,
    val time: String,
    val isThinking: Boolean = false
)

data class ExecutionStep(
    val icon: ImageVector,
    val action: String,
    val details: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    taskManager: TaskManager,
    onNavigateToOldUI: () -> Unit,
    onVoiceInput: ((String) -> Unit) -> Unit,
    onVoiceStart: (((String) -> Unit) -> Unit)? = null,
    onVoiceStop: (() -> Unit)? = null,
    onOpenSettings: (String) -> Unit,
    checkScreenCapturePermission: (() -> Unit) -> Unit,
    saveTaskToHistory: (String) -> Unit = {},
    loadChatHistory: () -> List<ChatMessage>,
    saveChatHistory: (List<ChatMessage>) -> Unit,
    clearChatHistory: () -> Unit
) {
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isExecuting by remember { mutableStateOf(false) }
    
    // 加载历史聊天记录，如果为空则显示欢迎语
    val initialMessages = remember {
        val history = loadChatHistory()
        if (history.isEmpty()) {
            listOf(ChatMessage(1, true, "你好！我是 Boss助手。请告诉我你想自动执行什么任务？", "10:00"))
        } else {
            history
        }
    }
    var messages by remember { mutableStateOf(initialMessages) }
    var inputText by remember { mutableStateOf("") }
    var executionSteps by remember { mutableStateOf<List<ExecutionStep>>(emptyList()) }
    var currentStepIndex by remember { mutableStateOf(-1) }
    var taskJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var completionResult by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 聊天记录变化时自动保存
    LaunchedEffect(messages) {
        saveChatHistory(messages)
    }
    
    // 主界面和弹框都在同一个Box中，弹框在最上层
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
    ) {
        // Ambient background glows
        Box(
            modifier = Modifier
                .offset((-100).dp, (-100).dp)
                .size(400.dp)
                .background(
                    Color(0xFF6366F1).copy(alpha = 0.1f),
                    CircleShape
                )
                .blur(100.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(50.dp, 50.dp)
                .size(350.dp)
                .background(
                    Color(0xFF22D3EE).copy(alpha = 0.1f),
                    CircleShape
                )
                .blur(100.dp)
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StatusBar(isExecuting = isExecuting)
            },
            bottomBar = {
                BottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentTab) {
                    MainTab.HOME -> HomeScreen(
                        messages = messages,
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        onVoiceInput = onVoiceInput,
                        onVoiceStart = onVoiceStart,
                        onVoiceStop = onVoiceStop,
                        onClearChat = {
                            // 清空聊天记录
                            clearChatHistory()
                            messages = listOf(
                                ChatMessage(1, true, "你好！我是 Boss助手。请告诉我你想自动执行什么任务？", "10:00")
                            )
                        },
                        onReExecute = { taskText ->
                            // 重新执行任务
                            inputText = taskText
                        },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                val taskDesc = inputText
                                messages = messages + ChatMessage(
                                    System.currentTimeMillis(),
                                    false,
                                    taskDesc,
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                )
                                inputText = ""
                                
                                // 检查权限后执行任务
                                checkScreenCapturePermission {
                                    isExecuting = true
                                    
                                    // 真正执行任务
                                    taskJob = coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        withContext(Dispatchers.Main) {
                                            messages = messages + ChatMessage(
                                                System.currentTimeMillis(),
                                                true,
                                                "收到。正在分析任务...",
                                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                                                isThinking = true
                                            )
                                        }
                                        
                                        // 创建任务对象
                                        val task = Task(
                                            id = System.currentTimeMillis(),
                                            title = taskDesc.take(20),
                                            description = taskDesc,
                                            status = TaskStatus.PENDING
                                        )
                                        
                                        // 设置悬浮窗停止回调
                                        com.aiautomation.service.FloatWindowManager.onStopRequested = {
                                            android.util.Log.d("StopButton", "悬浮窗停止按钮被点击")
                                            // 停止 TaskManager
                                            taskManager.stop()
                                            // 取消协程
                                            taskJob?.cancel()
                                            // 更新 UI
                                            coroutineScope.launch(Dispatchers.Main) {
                                                isExecuting = false
                                                executionSteps = emptyList()
                                                currentStepIndex = -1
                                                messages = messages.map { 
                                                    if (it.isThinking) it.copy(
                                                        text = "任务已停止 ⛔",
                                                        isThinking = false
                                                    ) else it 
                                                }
                                            }
                                        }
                                        
                                        // 设置进度回调
                                        taskManager.progressCallback = object : TaskManager.ProgressCallback {
                                            override fun onStatusUpdate(status: String) {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    // 更新步骤显示
                                                }
                                            }
                                            
                                            override fun onStepCompleted(step: Int, total: Int) {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    val stepList = listOf(
                                                        ExecutionStep(Icons.Default.Search, "分析屏幕", "AI视觉分析"),
                                                        ExecutionStep(Icons.Default.Settings, "规划操作", "生成执行序列"),
                                                        ExecutionStep(Icons.Default.PlayArrow, "执行中", "$step/$total")
                                                    )
                                                    executionSteps = stepList
                                                    currentStepIndex = if (step < total) 2 else -1
                                                }
                                            }
                                            
                                            override fun onTaskCompleted(success: Boolean) {
                                                // 保留以保证兼容性，但主要使用 onTaskCompletedWithResult
                                            }
                                            
                                            override fun onTaskCompletedWithResult(success: Boolean, result: String) {
                                                android.util.Log.d("TaskCompletion", "onTaskCompletedWithResult called: success=$success, result=$result")
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    // 将结果显示到聊天界面
                                                    val displayResult = if (result.isBlank()) "任务执行完成" else result
                                                    messages = messages.map { 
                                                        if (it.isThinking) it.copy(
                                                            text = if (success) displayResult else "任务执行失败 ❌",
                                                            isThinking = false
                                                        ) else it 
                                                    }
                                                    isExecuting = false
                                                    executionSteps = emptyList()
                                                    currentStepIndex = -1
                                                    
                                                    // 发送通知
                                                    if (success) {
                                                        // 将应用切回前台显示弹框
                                                        val intent = android.content.Intent(context, NewMainActivity::class.java)
                                                        intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                                        context.startActivity(intent)
                                                        
                                                        sendNotification(context as android.app.Activity, taskDesc)
                                                        // 播放声音
                                                        if (AppSettings.isSoundEnabled(context)) {
                                                            playCompletionSound(context)
                                                        }
                                                        
                                                        // 稍微延迟显示弹框，确保应用已切回前台
                                                        kotlinx.coroutines.delay(300)
                                                        
                                                        // 显示完成提示框
                                                        completionResult = displayResult
                                                        android.util.Log.d("TaskCompletion", "Setting showCompletionDialog = true, result = $displayResult")
                                                        showCompletionDialog = true
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // 保存到历史
                                        saveTaskToHistory(taskDesc)
                                        
                                        // 重置 TaskManager 状态（新会话）
                                        taskManager.reset()
                                        
                                        // 真正执行任务
                                        val result = taskManager.executeTask(task)
                                        
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            messages = messages + ChatMessage(
                                                System.currentTimeMillis(),
                                                true,
                                                "执行出错：${e.message}",
                                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                            )
                                            isExecuting = false
                                            executionSteps = emptyList()
                                            currentStepIndex = -1
                                        }
                                    }
                                    }
                                }
                            }
                        },
                        onAbort = {
                            taskJob?.cancel()
                            isExecuting = false
                            executionSteps = emptyList()
                            currentStepIndex = -1
                            messages = messages + ChatMessage(
                                System.currentTimeMillis(),
                                true,
                                "任务已取消",
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            )
                        },
                        isExecuting = isExecuting,
                        executionSteps = executionSteps,
                        currentStepIndex = currentStepIndex
                    )
                    MainTab.USER -> UserScreen(
                        onNavigateToLogs = { onOpenSettings("logs") },
                        onAccessibilityClick = { onOpenSettings("accessibility") },
                        onOverlayClick = { onOpenSettings("overlay") },
                        onSettingsChanged = {
                            // 设置变化时的回调（可以用于重新加载配置）
                        }
                    )
                }
            }
        }
        
        // 任务完成提示框 - 在Box内作为最上层
        if (showCompletionDialog) {
            android.util.Log.d("TaskCompletion", "Rendering TaskCompletionDialog")
            com.aiautomation.ui.screens.TaskCompletionDialog(
                result = completionResult,
                onDismiss = {
                    android.util.Log.d("TaskCompletion", "Dialog dismissed")
                    showCompletionDialog = false
                },
                onCopy = {
                    // 复制结果到剪贴板
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("任务结果", completionResult)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun StatusBar(isExecuting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExecuting) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF34D399)
                )
            }
            // Battery icon
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(10.dp)
                    .border(1.dp, Color(0xFF64748B), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.75f)
                        .padding(1.dp)
                        .background(Color(0xFF94A3B8))
                )
            }
        }
    }
}

@Composable
fun BottomNavigation(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color(0xFF0B1120).copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                icon = Icons.Default.Home,
                isSelected = currentTab == MainTab.HOME,
                onClick = { onTabSelected(MainTab.HOME) }
            )
            NavItem(
                icon = Icons.Default.Person,
                isSelected = currentTab == MainTab.USER,
                onClick = { onTabSelected(MainTab.USER) }
            )
        }
    }
}

@Composable
fun NavItem(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Color(0xFF6366F1).copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) Color(0xFF6366F1) else Color(0xFF64748B)
        )
    }
}

