package com.aiautomation.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.aiautomation.ai.DoubaoApiClient
import com.aiautomation.automation.TaskManager
import com.aiautomation.data.local.ChatDatabase
import com.aiautomation.data.model.ChatMessage
import com.aiautomation.data.model.Conversation
import com.aiautomation.data.model.MessageStatus
import com.aiautomation.service.AutomationService
import com.aiautomation.service.FloatWindowManager
import com.aiautomation.service.MyAccessibilityService
import com.aiautomation.service.ScreenCaptureService
import com.aiautomation.ui.theme.AIAutomationTheme
import com.aiautomation.util.AppLog
import com.aiautomation.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Boss助手主Activity - 聊天优先模式
 * 权限检查通过后直接进入唯一聊天界面，侧边抽屉集成历史/记录/设置
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var database: ChatDatabase
    private lateinit var apiClient: DoubaoApiClient
    private var conversationId: Long = -1
    private var pendingMessage: String? = null
    
    // 屏幕录制权限请求
    private val screenCaptureRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                startScreenCaptureService(result.resultCode, data)
                // 等待服务启动完成（包括虚拟屏幕初始化）
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    pendingMessage?.let { msg ->
                        AppLog.d("MainActivity", "投屏授权完成，自动继续任务")
                        // 重新调用 sendMessage，此时 ScreenCaptureService.instance 应该已就绪
                        sendMessage(msg)
                        pendingMessage = null
                    } ?: run {
                        Toast.makeText(this@MainActivity, "屏幕录制已就绪", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            lifecycleScope.launch {
                val errorMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "❌ 用户拒绝了屏幕录制权限，无法执行自动化任务",
                    status = MessageStatus.ERROR
                )
                database.messageDao().insertMessage(errorMsg)
            }
            pendingMessage = null
        }
    }
    
    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AutomationService.ACTION_AUTOMATION_RESULT) {
                val convId = intent.getLongExtra(AutomationService.EXTRA_CONVERSATION_ID, -1L)
                if (convId == conversationId) {
                    val status = intent.getStringExtra(AutomationService.EXTRA_RESULT_STATUS)
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
        com.aiautomation.util.AppCtx.init(applicationContext)
        
        // 注册结果广播接收
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resultReceiver, IntentFilter(AutomationService.ACTION_AUTOMATION_RESULT), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resultReceiver, IntentFilter(AutomationService.ACTION_AUTOMATION_RESULT))
        }
        
        lifecycleScope.launch {
            conversationId = getOrCreateDefaultConversation()
        }
        
        setContent {
            AIAutomationTheme {
                PermissionGate { MainChatScreen() }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(resultReceiver) } catch (_: Exception) {}
    }
    
    private suspend fun getOrCreateDefaultConversation(): Long {
        return withContext(Dispatchers.IO) {
            val existing = database.conversationDao().getAllConversationsOnce()
            if (existing.isEmpty()) {
                val conv = Conversation(title = "Boss助手", contextId = null)
                database.conversationDao().insertConversation(conv)
            } else {
                existing.first().id
            }
        }
    }
    
    @Composable
    fun PermissionGate(content: @Composable () -> Unit) {
        val ctx = this
        var refreshTick by remember { mutableStateOf(0) }
        
        // 自动定时刷新权限状态（每2秒）
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(2000)
                refreshTick++
            }
        }

        val hasOverlay = remember(refreshTick) { PermissionUtils.hasOverlayPermission(ctx) }
        val hasAccessibility = remember(refreshTick) { PermissionUtils.isAccessibilityServiceEnabled(ctx, "com.aiautomation.service.MyAccessibilityService") }
        val hasProjection = remember(refreshTick) { ScreenCaptureService.instance != null }

        if (hasOverlay && hasAccessibility) {
            content()
            return
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 标题区域
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "🤖 Boss助手",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "开始之前，请完成以下权限设置",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Divider()
                        
                        // 权限列表
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PermissionItem(
                                title = "无障碍服务",
                                isGranted = hasAccessibility,
                                icon = "♿",
                                description = "控制屏幕元素和执行操作"
                            ) {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                            
                            PermissionItem(
                                title = "悬浮窗权限",
                                isGranted = hasOverlay,
                                icon = "🪟",
                                description = "显示操作提示和状态"
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    startActivity(Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    ))
                                }
                            }
                            
                            PermissionItem(
                                title = "屏幕录制",
                                isGranted = hasProjection,
                                icon = "📱",
                                description = "首次执行任务时弹出授权",
                                isOptional = true
                            ) {}
                        }
                        
                        Divider()
                        
                        // 底部按钮
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val allGranted = hasAccessibility && hasOverlay
                            
                            if (!allGranted) {
                                Text(
                                    "⚠️ 需要授予所有必需权限才能使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            Button(
                                onClick = { refreshTick++ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("刷新状态")
                            }
                            
                            Button(
                                onClick = { /* 权限通过后自动进入 */ },
                                enabled = allGranted,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    if (allGranted) Icons.Default.Check else Icons.Default.Lock,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (allGranted) "进入应用" else "等待权限授予")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionItem(
        title: String,
        isGranted: Boolean,
        icon: String,
        description: String,
        isOptional: Boolean = false,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isGranted) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else 
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        icon,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            if (isOptional) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "(可选)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isGranted) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "已授权",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isOptional) "未授权" else "需要授权",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
                if (!isGranted && !isOptional) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("去设置")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainChatScreen() {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var drawerTab by remember { mutableStateOf("history") } // history, records, settings
        
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        selectedTab = drawerTab,
                        onTabSelected = { drawerTab = it },
                        onClose = { scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            ChatScreen(
                onMenuClick = { scope.launch { drawerState.open() } }
            )
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DrawerContent(selectedTab: String, onTabSelected: (String) -> Unit, onClose: () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            // 标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(24.dp)
            ) {
                Text(
                    "Boss助手",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            // Tab 选择
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = selectedTab == "history",
                    onClick = { onTabSelected("history") },
                    label = { Text("历史记录") }
                )
                FilterChip(
                    selected = selectedTab == "records",
                    onClick = { onTabSelected("records") },
                    label = { Text("执行记录") }
                )
                FilterChip(
                    selected = selectedTab == "settings",
                    onClick = { onTabSelected("settings") },
                    label = { Text("设置") }
                )
            }
            
            Divider()
            
            // 内容区
            Box(Modifier.fillMaxSize()) {
                when (selectedTab) {
                    "history" -> HistoryContent(onClose)
                    "records" -> RecordsContent()
                    "settings" -> SettingsContent()
                }
            }
        }
    }
    
    // ========== 抽屉内容区 ==========
    
    @Composable
    fun HistoryContent(onClose: () -> Unit) {
        val messages = remember { mutableStateListOf<ChatMessage>() }
        
        LaunchedEffect(conversationId) {
            if (conversationId != -1L) {
                database.messageDao().getMessagesByConversation(conversationId)
                    .collectLatest { msgs ->
                        messages.clear()
                        messages.addAll(msgs.filter { it.role == "user" })
                    }
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        // 快速重新执行
                        sendMessage(msg.content)
                        onClose()
                    }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatTime(msg.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun RecordsContent() {
        val ctx = LocalContext.current
        var selected by remember { mutableStateOf<File?>(null) }
        val root = File(ctx.filesDir, "exec_records").apply { mkdirs() }
        val sessions = remember { mutableStateListOf<File>() }

        LaunchedEffect(Unit) {
            sessions.clear()
            sessions.addAll(root.listFiles()?.sortedByDescending { it.name } ?: emptyList())
        }

        if (selected == null) {
            Column(Modifier.fillMaxSize()) {
                // 清空按钮
                if (sessions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    root.listFiles()?.forEach { it.deleteRecursively() }
                                    sessions.clear()
                                    Toast.makeText(ctx, "已清空所有记录", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("清空全部")
                        }
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                items(sessions) { dir ->
                    val meta = runCatching { JSONObject(File(dir, "meta.json").readText()) }.getOrNull()
                    val title = meta?.optString("title") ?: dir.name
                    val start = meta?.optString("startTime") ?: ""
                    val ok = meta?.optBoolean("success")
                    Card(modifier = Modifier.fillMaxWidth().clickable { selected = dir }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text("开始: $start", style = MaterialTheme.typography.bodySmall)
                            if (ok != null) Text(
                                if (ok) "成功" else "失败",
                                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                }
            }
        } else {
            Column {
                TextButton(onClick = { selected = null }) { Text("返回") }
                val stepsFile = File(selected!!, "steps.jsonl")
                val lines = runCatching { stepsFile.readLines() }.getOrElse { emptyList() }
                val steps = lines.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
                LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(steps) { step ->
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                Text("步骤 ${step.optInt("step")}")
                                val ai = step.optString("ai", "")
                                if (ai.isNotEmpty()) Text("动作: $ai", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun SettingsContent() {
        val ctx = LocalContext.current
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("全部设置")
            }
            Button(
                onClick = { ctx.startActivity(Intent(ctx, LogsActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("查看日志")
            }
            Button(
                onClick = { clearAllMessages() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("清空聊天记录")
            }
        }
    }
    
    // ========== 主聊天界面 ==========
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatScreen(onMenuClick: () -> Unit) {
        val messages = remember { mutableStateListOf<ChatMessage>() }
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        
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
                    title = { Text("Boss助手") },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        // 清空聊天记录
                        IconButton(onClick = { clearAllMessages() }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空")
                        }
                        // 查看执行日志
                        IconButton(onClick = {
                            startActivity(Intent(this@MainActivity, LogsActivity::class.java))
                        }) {
                            Icon(Icons.Default.List, contentDescription = "日志")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说出你的需求...") },
                        enabled = !isLoading
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                sendMessage(inputText)
                                isLoading = true
                                inputText = ""
                                // 使用 launch 在异步结束后重置 loading
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    isLoading = false
                                }
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
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 280.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = if (isUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(time, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    
    // ========== 业务方法 ==========
    
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    private fun clearAllMessages() {
        lifecycleScope.launch {
            if (conversationId != -1L) {
                database.messageDao().deleteMessagesByConversation(conversationId)
                Toast.makeText(this@MainActivity, "清空成功", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sendMessage(content: String) {
        lifecycleScope.launch {
            try {
                AppLog.d("MainActivity", "开始执行任务: $content")
                
                // 先保存用户消息
                val userMessage = ChatMessage(
                    conversationId = conversationId,
                    role = "user",
                    content = content,
                    status = MessageStatus.SENT
                )
                database.messageDao().insertMessage(userMessage)
                
                // 实时检查无障碍服务
                if (MyAccessibilityService.instance == null || 
                    !PermissionUtils.isAccessibilityServiceEnabled(this@MainActivity, "com.aiautomation.service.MyAccessibilityService")) {
                    AppLog.e("MainActivity", "无障碍服务未启用")
                    val errorMsg = ChatMessage(
                        conversationId = conversationId,
                        role = "assistant",
                        content = "❌ 无障碍服务未启用，请前往系统设置开启【Boss助手】的无障碍权限",
                        status = MessageStatus.ERROR
                    )
                    database.messageDao().insertMessage(errorMsg)
                    return@launch
                }
                
                // 检查悬浮窗权限
                if (!PermissionUtils.hasOverlayPermission(this@MainActivity)) {
                    AppLog.e("MainActivity", "悬浮窗权限未授予")
                    val errorMsg = ChatMessage(
                        conversationId = conversationId,
                        role = "assistant",
                        content = "❌ 悬浮窗权限未授予，请前往系统设置开启【Boss助手】的悬浮窗权限",
                        status = MessageStatus.ERROR
                    )
                    database.messageDao().insertMessage(errorMsg)
                    return@launch
                }
                
                // 检查屏幕录制
                if (ScreenCaptureService.instance == null) {
                    pendingMessage = content
                    AppLog.e("MainActivity", "屏幕录制未就绪，正在申请权限…")
                    val tipMsg = ChatMessage(
                        conversationId = conversationId,
                        role = "assistant",
                        content = "首次使用需要授权屏幕录制权限，请在弹出的系统对话框中点击【立即开始】",
                        status = MessageStatus.SENT
                    )
                    database.messageDao().insertMessage(tipMsg)
                    requestScreenCapture()
                    return@launch
                }
                
                if (checkOverlayPermission()) {
                    if (!FloatWindowManager.isInitialized()) {
                        FloatWindowManager.init(this@MainActivity)
                    }
                }
                
                val startMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "开始执行任务: $content",
                    status = MessageStatus.SENT
                )
                database.messageDao().insertMessage(startMsg)
                
                val svc = Intent(this@MainActivity, AutomationService::class.java)
                    .putExtra(AutomationService.EXTRA_TASK_TEXT, content)
                    .putExtra(AutomationService.EXTRA_CONVERSATION_ID, conversationId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)

                val infoMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "任务已在后台执行，稍后将反馈结果…",
                    status = MessageStatus.SENT
                )
                database.messageDao().insertMessage(infoMsg)
            } catch (e: Exception) {
                AppLog.e("MainActivity", "任务执行异常: ${e.message}")
                val errorMsg = ChatMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "错误: ${e.message}",
                    status = MessageStatus.ERROR
                )
                database.messageDao().insertMessage(errorMsg)
            }
        }
    }
    
    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
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
    
    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureRequest.launch(captureIntent)
    }
    
    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
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
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playCompletionSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            lifecycleScope.launch {
                kotlinx.coroutines.delay(300)
                toneGen.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
