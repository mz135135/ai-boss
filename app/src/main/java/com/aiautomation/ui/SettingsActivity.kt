package com.aiautomation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.aiautomation.ui.theme.AIAutomationTheme
import com.aiautomation.util.StepDelayPrefs

class SettingsActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAutomationTheme {
                SettingsScreen(
                    onSetDelay = { ms -> StepDelayPrefs.setDelayMs(this, ms) },
                    currentDelay = StepDelayPrefs.getDelayMs(this)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSetDelay: (Long)->Unit, currentDelay: Long) {
    val ctx = LocalContext.current

    var delayMs by remember { mutableStateOf(currentDelay) }
    var maxSteps by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.getMaxSteps(ctx).toString()) }
    var soundEnabled by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.isSoundEnabled(ctx)) }
    var stopShortcutEnabled by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.isStopShortcutEnabled(ctx)) }

    var recEnabled by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.isRecordingEnabled(ctx)) }
    var recShot by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.isRecordScreenshots(ctx)) }
    var logMaxMb by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.getLogMaxMb(ctx).toFloat()) }
    var drawerFb by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.isDrawerFallbackEnabled(ctx)) }

    var apiKey by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.getApiKey(ctx).orEmpty()) }
    var apiBaseUrl by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.getApiBaseUrl(ctx).orEmpty()) }
    var modelId by remember(ctx) { mutableStateOf(com.aiautomation.util.ExecPrefs.getModelId(ctx).orEmpty()) }

    val scrollState = rememberScrollState()
    
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { p ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(p)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("执行")

            Text("每步执行间隔：${delayMs}ms")
            Slider(
                value = delayMs.toFloat(),
                onValueChange = { delayMs = it.toLong() },
                valueRange = 500f..8000f,
                steps = 15
            )

            Text("最大步数")
            OutlinedTextField(
                value = maxSteps,
                onValueChange = { 
                    // 只允许输入数字
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        maxSteps = it
                    }
                },
                label = { Text("最大步数 (5-100)") },
                placeholder = { Text("20") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("声音提醒", modifier = Modifier.weight(1f))
                Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("双击音量减中断任务", modifier = Modifier.weight(1f))
                Switch(checked = stopShortcutEnabled, onCheckedChange = { stopShortcutEnabled = it })
            }

            Text("执行记录")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("启用记录", modifier = Modifier.weight(1f))
                Switch(checked = recEnabled, onCheckedChange = { recEnabled = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("保存步骤截图", modifier = Modifier.weight(1f))
                Switch(checked = recShot, onCheckedChange = { recShot = it })
            }

            Text("日志文件上限：${logMaxMb.toInt()}MB")
            Slider(value = logMaxMb, onValueChange = { logMaxMb = it }, valueRange = 1f..10f, steps = 8)

            Text("打开应用兜底（抽屉/搜索）")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("启用兜底", modifier = Modifier.weight(1f))
                Switch(checked = drawerFb, onCheckedChange = { drawerFb = it })
            }

            Divider()
            Text("API（留空则使用默认硬编码）")

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = { apiBaseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://ark.cn-beijing.volces.com/api/v3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model ID") },
                placeholder = { Text("doubao-seed-1-6-flash-250828") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    // 保存偏好
                    com.aiautomation.util.ExecPrefs.setLogMaxMb(ctx, logMaxMb.toInt())
                    com.aiautomation.util.ExecPrefs.setRecordingEnabled(ctx, recEnabled)
                    com.aiautomation.util.ExecPrefs.setRecordScreenshots(ctx, recShot)
                    com.aiautomation.util.ExecPrefs.setDrawerFallbackEnabled(ctx, drawerFb)
                    
                    // 最大步数从字符串转换为整数，限制在 5-100 之间
                    val stepsValue = maxSteps.toIntOrNull()?.coerceIn(5, 100) ?: 20
                    com.aiautomation.util.ExecPrefs.setMaxSteps(ctx, stepsValue)
                    
                    com.aiautomation.util.ExecPrefs.setSoundEnabled(ctx, soundEnabled)
                    com.aiautomation.util.ExecPrefs.setStopShortcutEnabled(ctx, stopShortcutEnabled)

                    com.aiautomation.util.ExecPrefs.setApiKey(ctx, apiKey.trim().ifEmpty { null })
                    com.aiautomation.util.ExecPrefs.setApiBaseUrl(ctx, apiBaseUrl.trim().ifEmpty { null })
                    com.aiautomation.util.ExecPrefs.setModelId(ctx, modelId.trim().ifEmpty { null })

                    onSetDelay(delayMs)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }

            Text("说明：较短的间隔更快但可能不稳定；最大步数过大可能更耗时。截图保存将写入系统相册（Pictures/Boss助手）。")
        }
    }
}
