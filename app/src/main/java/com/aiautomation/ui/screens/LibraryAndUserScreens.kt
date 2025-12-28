package com.aiautomation.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import com.aiautomation.util.PermissionUtils
import com.aiautomation.util.AppSettings
import androidx.compose.material.icons.filled.Notifications
import android.content.Context
import java.io.File

// ========== LIBRARY SCREEN ==========
@Composable
fun LibraryScreen(onTaskClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "命令库",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Favorites section
        SectionTitle("常用")
        Spacer(modifier = Modifier.height(16.dp))
        
        TaskCard(
            title = "给老婆发微信",
            description = "打开微信 -> 搜索老婆 -> 发送 '今晚回家吃饭'",
            icon = Icons.Default.Phone,
            iconColor = Color(0xFF22D3EE),
            onClick = { onTaskClick("给老婆发微信") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        TaskCard(
            title = "一键清理垃圾",
            description = "打开手机管家 -> 深度清理 -> 确认",
            icon = Icons.Default.Delete,
            iconColor = Color(0xFFFB7185),
            onClick = { onTaskClick("清理手机垃圾") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        TaskCard(
            title = "早安模式",
            description = "播报天气 -> 打开日历 -> 播放音乐",
            icon = Icons.Default.DateRange,
            iconColor = Color(0xFFFBBF24),
            onClick = { onTaskClick("早安模式") }
        )

        // Tools section
        Spacer(modifier = Modifier.height(32.dp))
        SectionTitle("工具")
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToolCard(
                title = "任务历史",
                icon = Icons.Default.Refresh,
                iconColor = Color(0xFFEC4899),
                modifier = Modifier.weight(1f),
                onClick = { /* TODO */ }
            )
            ToolCard(
                title = "使用帮助",
                icon = Icons.Default.Info,
                iconColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { /* TODO */ }
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = Color(0xFF64748B)
    )
}

@Composable
fun TaskCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ToolCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

// ========== USER/SETTINGS SCREEN ==========
@Composable
fun UserScreen(
    onNavigateToLogs: () -> Unit = {},
    onAccessibilityClick: () -> Unit = {},
    onOverlayClick: () -> Unit = {},
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // 权限实时检测
    var hasAccessibility by remember { mutableStateOf(false) }
    var hasOverlay by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while (true) {
            hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(
                context, 
                "com.aiautomation.service.MyAccessibilityService"
            )
            hasOverlay = PermissionUtils.hasOverlayPermission(context)
            delay(1000) // 每秒1检查
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // User profile header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF312E81).copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.padding(32.dp, 32.dp, 32.dp, 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF22D3EE), Color(0xFF6366F1))
                            ),
                            shape = CircleShape
                        )
                        .padding(2.dp)
                        .background(Color(0xFF1E293B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "Boss助手用户",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.1f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "专业版",
                            fontSize = 12.sp,
                            color = Color(0xFF818CF8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Content
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Log entry point
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToLogs),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "运行日志",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = "查看历史执行记录与报错",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                }
            }

            // Settings section
            Spacer(modifier = Modifier.height(32.dp))
            SectionTitle("基本设置")
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    SettingsSliderItem(
                        title = "最大步骤数",
                        icon = Icons.Default.Settings,
                        iconColor = Color(0xFF22D3EE),
                        value = AppSettings.getMaxSteps(context),
                        valueRange = 10f..200f,
                        valueLabel = { "${it.toInt()}步" },
                        onValueChange = { 
                            AppSettings.setMaxSteps(context, it.toInt())
                            onSettingsChanged()
                        }
                    )
                    SettingsSliderItem(
                        title = "步骤间隔时间",
                        icon = Icons.Default.DateRange,
                        iconColor = Color(0xFFFB923C),
                        value = (AppSettings.getStepInterval(context) / 100).toInt(),
                        valueRange = 2f..50f,
                        valueLabel = { "${(it * 100).toInt()}ms" },
                        onValueChange = { 
                            AppSettings.setStepInterval(context, (it * 100).toLong())
                            onSettingsChanged()
                        }
                    )
                    SettingsSwitchItem(
                        title = "任务完成声音",
                        icon = Icons.Default.Notifications,
                        iconColor = Color(0xFF10B981),
                        isEnabled = AppSettings.isSoundEnabled(context),
                        onToggle = { 
                            AppSettings.setSoundEnabled(context, it)
                            onSettingsChanged()
                        }
                    )
                    SettingsSwitchItem(
                        title = "显示操作坐标",
                        icon = Icons.Default.Place,
                        iconColor = Color(0xFFEC4899),
                        isEnabled = AppSettings.isShowCoordinatesEnabled(context),
                        onToggle = { 
                            AppSettings.setShowCoordinatesEnabled(context, it)
                            onSettingsChanged()
                        }
                    )
                }
            }

            // Permissions section
            Spacer(modifier = Modifier.height(32.dp))
            SectionTitle("权限管理")
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    PermissionItem(
                        title = "无障碍服务",
                        icon = Icons.Default.Warning,
                        iconColor = Color(0xFFFB923C),
                        isEnabled = hasAccessibility,
                        onClick = onAccessibilityClick
                    )
                    PermissionItem(
                        title = "悬浮窗权限",
                        icon = Icons.Default.Settings,
                        iconColor = Color(0xFF6366F1),
                        isEnabled = hasOverlay,
                        onClick = onOverlayClick
                    )
                }
            }

            // General section
            Spacer(modifier = Modifier.height(32.dp))
            SectionTitle("常规")
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    GeneralItem(
                        title = "清除缓存",
                        icon = Icons.Default.Refresh,
                        value = getCacheSize(context),
                        onClick = {
                            clearCache(context)
                            android.widget.Toast.makeText(context, "已清除缓存", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    GeneralItem(
                        title = "关于Boss助手",
                        icon = Icons.Default.Info,
                        value = "v1.0.3"
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    isEnabled: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.weight(1f)
        )
        
        // Toggle switch
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(16.dp)
                .background(
                    if (isEnabled) Color(0xFF10B981).copy(alpha = 0.2f)
                    else Color(0xFF64748B).copy(alpha = 0.2f),
                    RoundedCornerShape(8.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(2.dp)
                    .size(12.dp)
                    .background(
                        if (isEnabled) Color(0xFF10B981) else Color(0xFF64748B),
                        CircleShape
                    )
                    .shadow(4.dp, CircleShape)
            )
        }
    }
}

@Composable
fun GeneralItem(
    title: String,
    icon: ImageVector,
    value: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(sliderValue),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = iconColor
            )
        }
        
        Slider(
            value = sliderValue,
            onValueChange = { 
                sliderValue = it
            },
            onValueChangeFinished = {
                onValueChange(sliderValue)
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = iconColor,
                activeTrackColor = iconColor.copy(alpha = 0.8f),
                inactiveTrackColor = Color(0xFF334155)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.weight(1f)
        )
        
        // Toggle switch
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .background(
                    if (isEnabled) iconColor.copy(alpha = 0.3f)
                    else Color(0xFF64748B).copy(alpha = 0.2f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(3.dp)
                    .size(18.dp)
                    .background(
                        if (isEnabled) iconColor else Color(0xFF64748B),
                        CircleShape
                    )
                    .shadow(4.dp, CircleShape)
            )
        }
    }
}

// 缓存管理函数
private fun getCacheSize(context: Context): String {
    var size = 0L
    try {
        size += getFolderSize(context.cacheDir)
        size += getFolderSize(context.externalCacheDir)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return formatSize(size)
}

private fun clearCache(context: Context) {
    try {
        deleteDir(context.cacheDir)
        context.externalCacheDir?.let { deleteDir(it) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getFolderSize(dir: File?): Long {
    var size = 0L
    dir?.listFiles()?.forEach { file ->
        size += if (file.isDirectory) {
            getFolderSize(file)
        } else {
            file.length()
        }
    }
    return size
}

private fun deleteDir(dir: File?) {
    dir?.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            deleteDir(file)
        } else {
            file.delete()
        }
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "${size / (1024 * 1024)}MB"
    }
}
