package com.aiautomation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiautomation.ui.theme.AIAutomationTheme
import com.aiautomation.util.AppLog

class LogsActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAutomationTheme {
                LogsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(AppLog.all()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行日志") },
                actions = {
                    TextButton(onClick = { AppLog.clear(); logs = AppLog.all() }) { Text("清空") }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logs.joinToString("\n"))
                        }
                        context.startActivity(Intent.createChooser(share, "分享日志"))
                    }) { Text("分享") }
                }
            )
        }
    ) { p ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(p),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(logs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
