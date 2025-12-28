package com.aiautomation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiautomation.ui.ChatMessage
import com.aiautomation.ui.ExecutionStep
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width

@Composable
fun HomeScreen(
    messages: List<ChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: ((String) -> Unit) -> Unit, // 暂时保留兼容
    onVoiceStart: (((String) -> Unit) -> Unit)? = null,
    onVoiceStop: (() -> Unit)? = null,
    onAbort: () -> Unit,
    isExecuting: Boolean,
    executionSteps: List<ExecutionStep>,
    currentStepIndex: Int,
    onClearChat: () -> Unit,
    onReExecute: (String) -> Unit
) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0B1120).copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp, 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "你好,",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                    Text(
                        text = "指挥官",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // 清空按钮
                if (messages.size > 1) {
                    IconButton(
                        onClick = onClearChat,
                        enabled = !isExecuting
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "清空聊天",
                            tint = if (isExecuting) Color(0xFF475569) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    onReExecute = if (!message.isAI && !isExecuting) {
                        { onReExecute(message.text) }
                    } else null
                )
            }

            // Execution steps overlay
            if (executionSteps.isNotEmpty()) {
                item {
                    ExecutionCard(
                        steps = executionSteps,
                        currentStepIndex = currentStepIndex,
                        onAbort = onAbort
                    )
                }
            }
        }

        // Input area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 8.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.8f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 语音按钮 - 按住录音
                var isRecording by remember { mutableStateOf(false) }
                var recordedText by remember { mutableStateOf("") }
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color(0xFFEF4444)
                            else if (isExecuting) Color(0xFF475569)
                            else Color(0xFF1E293B)
                        )
                        .pointerInput(Unit) {
                            if (!isExecuting) {
                                detectTapGestures(
                                    onPress = {
                                        // 按下：开始录音
                                        isRecording = true
                                        recordedText = ""
                                        
                                        // 传递回调来接收识别结果
                                        onVoiceStart?.invoke { text ->
                                            recordedText = text
                                            onInputChange(text)
                                        }
                                        
                                        tryAwaitRelease()
                                        
                                        // 松开：停止录音
                                        isRecording = false
                                        onVoiceStop?.invoke()
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.KeyboardVoice,
                        contentDescription = if (isRecording) "松开停止" else "按住录音",
                        tint = if (isRecording) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            "告诉我要做什么...",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (inputText.isNotBlank())
                                Modifier.background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFF9333EA))
                                    ),
                                    shape = CircleShape
                                )
                            else Modifier.background(Color(0xFF475569), CircleShape)
                        )
                        .clickable(enabled = inputText.isNotBlank(), onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onReExecute: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isAI) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (message.isAI) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF6366F1), CircleShape)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (message.isAI) 4.dp else 16.dp,
                topEnd = if (message.isAI) 16.dp else 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (message.isAI)
                Color(0xFF151E32).copy(alpha = 0.6f)
            else
                Color(0xFF6366F1),
            border = if (message.isAI)
                BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            else null,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (message.isAI) Color(0xFFCBD5E1) else Color.White,
                    lineHeight = 20.sp
                )
                
                if (message.isThinking) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            ThinkingDot(delay = index * 200L)
                        }
                    }
                }
            }
        }
        
        // 重新执行按钮（仅用户消息）
        if (!message.isAI && onReExecute != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        Color(0xFF1E293B).copy(alpha = 0.8f),
                        CircleShape
                    )
                    .clickable(onClick = onReExecute)
                    .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "重新执行",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ThinkingDot(delay: Long) {
    var alpha by remember { mutableStateOf(0.3f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(delay)
            alpha = 0.8f
            kotlinx.coroutines.delay(400)
            alpha = 0.3f
            kotlinx.coroutines.delay(600 - delay)
        }
    }

    Box(
        modifier = Modifier
            .size(4.dp, 16.dp)
            .background(Color(0xFF22D3EE).copy(alpha = alpha), RoundedCornerShape(2.dp))
    )
}

@Composable
fun ExecutionCard(
    steps: List<ExecutionStep>,
    currentStepIndex: Int,
    onAbort: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.8f),
        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "执行中",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF6366F1)
                    )
                }
                
                TextButton(
                    onClick = onAbort,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.1f),
                        contentColor = Color(0xFFEF4444)
                    )
                ) {
                    Text("中止", fontSize = 12.sp)
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // Steps
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    StepItem(
                        step = step,
                        isActive = index == currentStepIndex,
                        isCompleted = index < currentStepIndex
                    )
                }
            }
        }
    }
}

@Composable
fun StepItem(
    step: ExecutionStep,
    isActive: Boolean,
    isCompleted: Boolean
) {
    val backgroundColor = when {
        isActive -> Color(0xFF6366F1).copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val borderColor = if (isActive) Color(0xFF6366F1).copy(alpha = 0.5f) else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = if (isActive) BorderStroke(1.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        when {
                            isActive -> Color(0xFF6366F1)
                            isCompleted -> Color(0xFF10B981)
                            else -> Color(0xFF475569)
                        },
                        CircleShape
                    )
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCompleted) Icons.Default.Check else step.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = step.action,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                if (isActive) {
                    Text(
                        text = step.details,
                        fontSize = 12.sp,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Loading indicator
            if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF6366F1)
                )
            }
        }
    }
}

@Composable
fun TaskCompletionDialog(
    result: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF34D399))
                            ),
                            shape = CircleShape
                        )
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "任务完成",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Divider
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF9333EA))
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Result text
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = result,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Copy button
                    androidx.compose.material3.OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF6366F1)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制结果", fontSize = 14.sp)
                    }
                    
                    // Dismiss button
                    androidx.compose.material3.Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("返回", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
