package com.aiautomation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.aiautomation.ui.theme.AIAutomationTheme
import org.json.JSONObject
import java.io.File

class ExecRecordsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { 
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
                    secondary = androidx.compose.ui.graphics.Color(0xFF22D3EE),
                    background = androidx.compose.ui.graphics.Color(0xFF0B1120),
                    surface = androidx.compose.ui.graphics.Color(0xFF1E293B),
                    onPrimary = androidx.compose.ui.graphics.Color.White,
                    onSecondary = androidx.compose.ui.graphics.Color.White,
                    onBackground = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                    onSurface = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                )
            ) { 
                RecordsScreen() 
            } 
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen() {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<File?>(null) }
    val root = File(ctx.filesDir, "exec_records").apply { mkdirs() }
    val sessions = remember { mutableStateListOf<File>() }

    LaunchedEffect(Unit) {
        sessions.clear()
        sessions.addAll(root.listFiles()?.sortedByDescending { it.name } ?: emptyList())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
    ) {
        if (selected == null) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { 
                    TopAppBar(
                        title = { Text("执行记录", color = Color.White) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
                        ),
                        actions = {
                            TextButton(
                                onClick = {
                                    try {
                                        root.listFiles()?.forEach { it.deleteRecursively() }
                                        sessions.clear()
                                        android.widget.Toast.makeText(ctx, "已清空所有记录", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(ctx, "清空失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = sessions.isNotEmpty()
                            ) {
                                Text("清空", color = Color(0xFF6366F1))
                            }
                        }
                    )
                }
            ) { p ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(p),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions) { dir ->
                        val meta = runCatching { JSONObject(File(dir, "meta.json").readText()) }.getOrNull()
                        val title = meta?.optString("title") ?: dir.name
                        val start = meta?.optString("startTime") ?: ""
                        val end = meta?.optString("endTime") ?: ""
                        val ok = meta?.optBoolean("success")
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selected = dir },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B)
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text("开始: $start  结束: $end", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                                if (ok != null) Text(
                                    if (ok) "结果: 成功" else "结果: 失败",
                                    color = if (ok == true) Color(0xFF10B981) else Color(0xFFEF4444),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val stepsFile = File(selected!!, "steps.jsonl")
            val lines = runCatching { stepsFile.readLines() }.getOrElse { emptyList() }
            val steps = lines.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text("步骤回放", color = Color.White) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
                        ),
                        navigationIcon = {
                            TextButton(onClick = { selected = null }) { 
                                Text("返回", color = Color(0xFF6366F1)) 
                            }
                        }
                    )
                }
            ) { p ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(p),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(steps) { step ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B)
                            )
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "步骤 ${step.optInt("step")}  时间 ${step.optString("time")}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val shot = step.optString("screenshot", "")
                                if (shot.isNotEmpty()) {
                                    Image(
                                        painter = rememberAsyncImagePainter("file://$shot"),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(360.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                val ai = step.optString("ai", "")
                                val action = step.optString("action", "")
                                val top = step.optString("topPackage", "")
                                val ok = step.opt("success")
                                if (ai.isNotEmpty()) Text(
                                    "AI: $ai",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                                if (action.isNotEmpty()) Text(
                                    "动作: $action",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                                if (top.isNotEmpty()) Text(
                                    "顶层包: $top",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                                if (ok is Boolean) Text(
                                    if (ok) "执行: 成功" else "执行: 失败",
                                    color = if (ok) Color(0xFF10B981) else Color(0xFFEF4444),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}