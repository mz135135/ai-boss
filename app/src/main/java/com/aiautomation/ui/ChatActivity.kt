package com.aiautomation.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aiautomation.ai.DoubaoApiClient
import com.aiautomation.data.local.ChatDatabase
import com.aiautomation.data.model.ChatMessage
import com.aiautomation.data.model.Conversation
import com.aiautomation.data.model.MessageStatus
import com.aiautomation.automation.TaskManager
import com.aiautomation.service.FloatWindowManager
import com.aiautomation.service.MyAccessibilityService
import com.aiautomation.service.ScreenCaptureService
import com.aiautomation.ui.theme.AIAutomationTheme
import com.aiautomation.util.AppLog
import com.aiautomation.voice.VoiceRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : ComponentActivity() {
    
    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }
    
    private lateinit var database: ChatDatabase
    private lateinit var apiClient: DoubaoApiClient
    private var conversationId: Long = -1
    private var contextId: String? = null
    private var pendingMessage: String? = null
    
    // 语音识别
    private var voiceRecognizer: VoiceRecognizer? = null
    private var isVoiceModelInitialized = false
    
    // 屏幕录制权限请求
    private val screenCaptureRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("ChatActivity", "屏幕录制权限结果: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                android.util.Log.d("ChatActivity", "权限授予成功，启动屏幕录制服务")
                startScreenCaptureService(result.resultCode, data)
                // 如果之前有待执行任务，自动继续
                pendingMessage?.let { msg ->
                    android.util.Log.d("ChatActivity", "检测到待执行任务，自动继续: $msg")
                    AppLog.d("ChatActivity", "投屏授权完成，自动继续任务")
                    lifecycleScope.launch { executeTaskFromMessage(msg) }
                    pendingMessage = null
                } ?: run {
                    Toast.makeText(this, "屏幕录制已就绪", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            android.util.Log.w("ChatActivity", "用户拒绝屏幕录制权限")
            Toast.makeText(this, "需要屏幕录制权限才能执行任务", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val resultReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == com.aiautomation.service.AutomationService.ACTION_AUTOMATION_RESULT) {
                val convId = intent.getLongExtra(com.aiautomation.service.AutomationService.EXTRA_CONVERSATION_ID, -1L)
                if (convId == conversationId) {
                    val status = intent.getStringExtra(com.aiautomation.service.AutomationService.EXTRA_RESULT_STATUS)
                    lifecycleScope.launch {
                        val text = when (status) {
                            com.aiautomation.data.model.TaskStatus.SUCCESS.name -> "✅ 任务执行成功！"
                            com.aiautomation.data.model.TaskStatus.FAILED.name -> "❌ 任务执行失败"
                            else -> "任务结束"
                        }
                        val msg = ChatMessage(conversationId = conversationId, role = "assistant", content = text, status = MessageStatus.SENT)
                        database.messageDao().insertMessage(msg)
                        playCompletionSound()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = ChatDatabase.getDatabase(this)
        apiClient = DoubaoApiClient.getInstance()
        conversationId = intent.getLongExtra("conversation_id", -1)
        
        if (conversationId == -1L) {
            lifecycleScope.launch {
                conversationId = createNewConversation()
            }
        }
        
        // 初始化语音识别
        initVoiceRecognizer()
        
        // 注册结果广播接收 (Android 13+ 需要指定 flag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resultReceiver, android.content.IntentFilter(com.aiautomation.service.AutomationService.ACTION_AUTOMATION_RESULT), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resultReceiver, android.content.IntentFilter(com.aiautomation.service.AutomationService.ACTION_AUTOMATION_RESULT))
        }

        setContent {
            AIAutomationTheme {
                ChatScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(resultReceiver) } catch (_: Exception) {}
        voiceRecognizer?.release()
    }
    
    private suspend fun createNewConversation(): Long {
        return withContext(Dispatchers.IO) {
            val conversation = Conversation(
                title = "新对话 ${System.currentTimeMillis()}",
                contextId = null
            )
            database.conversationDao().insertConversation(conversation)
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatScreen() {
        val messages = remember { mutableStateListOf<ChatMessage>() }
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var isRecording by remember { mutableStateOf(false) }
        var partialVoiceText by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val context = LocalContext.current
        
        LaunchedEffect(conversationId) {
            if (conversationId != -1L) {
                database.messageDao().getMessagesByConversation(conversationId)
                    .collectLatest { msgs ->
                        messages.clear()
                        messages.addAll(msgs)
                    }
            }
        }
        
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI 自动化助手") },
                    actions = {
                        IconButton(onClick = { clearConversation() }) {
                            Text("清空", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(context, LogsActivity::class.java))
                        }) { Text("日志", style = MaterialTheme.typography.bodyMedium) }
                        IconButton(onClick = {
                            context.startActivity(Intent(context, ExecRecordsActivity::class.java))
                        }) { Text("执行记录", style = MaterialTheme.typography.bodyMedium) }
                        IconButton(onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }) { Text("设置", style = MaterialTheme.typography.bodyMedium) }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        MessageItem(message)
                    }
                    
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (isRecording) partialVoiceText else inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isRecording) "正在录音..." else "输入任务描述...") },
                        enabled = !isLoading && !isRecording,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 语音输入按钮
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                stopVoiceInput { text ->
                                    inputText = text
                                    isRecording = false
                                    partialVoiceText = ""
                                }
                            } else {
                                startVoiceInput(
                                    onPartial = { partialVoiceText = it },
                                    onResult = { text ->
                                        inputText = text
                                        isRecording = false
                                        partialVoiceText = ""
                                    },
                                    onStart = { isRecording = true }
                                )
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "停止录音" else "语音输入",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                sendMessage(inputText) { isLoading = it }
                                inputText = ""
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
    
    @Composable
    fun MessageItem(message: ChatMessage) {
        val isUser = message.role == "user"
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = if (isUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    
    private fun sendMessage(content: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
android.util.Log.d("ChatActivity", "========== 开始执行任务 ==========")
AppLog.d("ChatActivity", "开始执行任务: $content")
                android.util.Log.d("ChatActivity", "任务内容: $content")
                setLoading(true)
                
                // 如果屏幕录制未启动，先申请权限，成功后自动继续，不保存消息以避免重复
                if (ScreenCaptureService.instance == null) {
                    pendingMessage = content
                    android.util.Log.e("ChatActivity", "屏幕录制服务未启动！请求权限...")
                    AppLog.e("ChatActivity", "屏幕录制未就绪，正在申请权限…")
                    requestScreenCapture()
                    setLoading(false)
                    return@launch
                }

                // 保存用户消息
                val userMessage = ChatMessage(
                    conversationId = conversationId,
                    role = "user",
                    content = content,
                    status = MessageStatus.SENT
                )
                database.messageDao().insertMessage(userMessage)
                android.util.Log.d("ChatActivity", "用户消息已保存")
                AppLog.d("ChatActivity", "用户消息已保存")
                
                // 检查无障碍服务
                android.util.Log.d("ChatActivity", "检查无障碍服务...")
                if (MyAccessibilityService.instance == null) {
                    android.util.Log.e("ChatActivity", "无障碍服务未启用！")
                    val errorMsg = ChatMessage(
                        conversationId = conversationId,
                        role = "assistant",
                        content = "请先启用无障碍服务",
                        status = MessageStatus.ERROR
                    )
                    database.messageDao().insertMessage(errorMsg)
                    setLoading(false)
                    return@launch
                }
android.util.Log.d("ChatActivity", "✓ 无障碍服务已启用")
AppLog.d("ChatActivity", "✓ 无障碍服务已启用")
                
                // 此处已在前置分支检查，不再重复
                
                // 检查并初始化悬浮窗
                android.util.Log.d("ChatActivity", "检查悬浮窗权限...")
                if (checkOverlayPermission()) {
                    android.util.Log.d("ChatActivity", "✓ 悬浮窗权限已授予")
                    AppLog.d("ChatActivity", "✓ 悬浮窗权限已授予")
                    // 检查悬浮窗是否已初始化
                    if (!FloatWindowManager.isInitialized()) {
                        android.util.Log.d("ChatActivity", "悬浮窗未初始化，准备初始化...")
                        FloatWindowManager.init(this@ChatActivity)
                    } else {
                        android.util.Log.d("ChatActivity", "✓ 悬浮窗已初始化")
                    }
                } else {
                    android.util.Log.w("ChatActivity", "悬浮窗权限未授予，请手动授予")
                }
                
                // 保存开始提示
                val startMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "开始执行任务: $content",
                    status = MessageStatus.SENT
                )
                database.messageDao().insertMessage(startMsg)
                
                // 启动后台 AutomationService 执行任务，避免 Activity 切后台被取消
                val svc = Intent(this@ChatActivity, com.aiautomation.service.AutomationService::class.java)
                    .putExtra(com.aiautomation.service.AutomationService.EXTRA_TASK_TEXT, content)
                    .putExtra(com.aiautomation.service.AutomationService.EXTRA_CONVERSATION_ID, conversationId)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)

                // 给出提示并等待结果广播
                val infoMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "任务已在后台执行，稍后将反馈结果…",
                    status = MessageStatus.SENT
                )
                database.messageDao().insertMessage(infoMsg)
            } catch (e: Exception) {
android.util.Log.e("ChatActivity", "任务执行异常", e)
AppLog.e("ChatActivity", "任务执行异常: ${e.message}")
                android.util.Log.e("ChatActivity", "异常堆栈: ${e.stackTraceToString()}")
                val errorMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "错误: ${e.message}",
                    status = MessageStatus.ERROR
                )
                database.messageDao().insertMessage(errorMsg)
            } finally {
                android.util.Log.d("ChatActivity", "========== 任务流程结束 ==========")
                setLoading(false)
            }
        }
    }
    
    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 请求悬浮窗权限
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return false
            }
        }
        return true
    }
    
    
    /**
     * 请求屏幕录制权限
     */
    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureRequest.launch(captureIntent)
    }
    
    /**
     * 启动屏幕录制服务
     */
    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        android.util.Log.d("ChatActivity", "准备启动 ScreenCaptureService, resultCode=$resultCode")
        
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            android.util.Log.d("ChatActivity", "屏幕录制服务已启动")
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "启动屏幕录制服务失败", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun executeTaskFromMessage(content: String): com.aiautomation.data.model.TaskStatus {
        android.util.Log.d("ChatActivity", "创建 TaskManager 并执行…")
        val taskManager = com.aiautomation.automation.TaskManager(
            this@ChatActivity,
            apiClient,
            DoubaoApiClient.DEFAULT_MODEL_ID
        )
        val task = com.aiautomation.data.model.Task(
            title = "自动化任务",
            description = content
        )
        android.util.Log.d("ChatActivity", "开始执行任务: $content")
        AppLog.d("ChatActivity", "开始执行任务: $content")
        val result = taskManager.executeTask(task)
        android.util.Log.d("ChatActivity", "任务执行完成，结果: $result")
        AppLog.d("ChatActivity", "任务执行完成，结果: $result")
        return result
    }

    private fun clearConversation() {
        lifecycleScope.launch {
            database.messageDao().deleteMessagesByConversation(conversationId)
        }
    }
    
    private fun playCompletionSound() {
        try {
            // 播放提示音
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            
            // 延迟释放资源
            lifecycleScope.launch {
                kotlinx.coroutines.delay(300)
                toneGen.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ============ 语音识别相关方法 ============
    
    /**
     * 初始化语音识别器
     */
    private fun initVoiceRecognizer() {
        voiceRecognizer = VoiceRecognizer(this)
        
        // 异步加载模型
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ChatActivity", "开始加载Vosk模型...")
                val success = voiceRecognizer?.initModel() ?: false
                isVoiceModelInitialized = success
                
                if (success) {
                    android.util.Log.d("ChatActivity", "Vosk模型加载成功")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "语音识别已就绪", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.util.Log.e("ChatActivity", "Vosk模型加载失败")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "初始化语音识别失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "语音识别初始化失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 检查录音权限
     */
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
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 开始语音输入
     */
    private fun startVoiceInput(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onStart: () -> Unit
    ) {
        if (!isVoiceModelInitialized) {
            Toast.makeText(this, "语音识别正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!checkAudioPermission()) {
            return
        }
        
        voiceRecognizer?.apply {
            onPartialResult = onPartial
            this.onResult = onResult
            onError = { error ->
                android.util.Log.e("ChatActivity", "语音识别错误: $error")
                Toast.makeText(this@ChatActivity, error, Toast.LENGTH_SHORT).show()
            }
            
            startListening()
            onStart()
            android.util.Log.d("ChatActivity", "开始语音识别")
        }
    }
    
    /**
     * 停止语音输入
     */
    private fun stopVoiceInput(onComplete: (String) -> Unit) {
        voiceRecognizer?.apply {
            stopListening()
            android.util.Log.d("ChatActivity", "停止语音识别")
        }
    }
}
