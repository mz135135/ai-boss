package com.aiautomation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.coroutines.resume

class MyAccessibilityService : AccessibilityService() {
    
    private val gestureHandler = Handler(Looper.getMainLooper())

    // 双击音量减中断（默认开启）
    private var lastVolDownAt: Long = 0L
    private val volDoublePressWindowMs = 420L
    
    // 存储最近的通知信息
    private val recentNotifications = mutableListOf<NotificationInfo>()
    private val maxNotifications = 10
    
    data class NotificationInfo(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long
    )
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // 监听通知事件
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            val notification = event.parcelableData as? android.app.Notification
            
            if (notification != null) {
                val title = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                
                if (title.isNotEmpty() || text.isNotEmpty()) {
                    synchronized(recentNotifications) {
                        recentNotifications.add(0, NotificationInfo(
                            packageName = packageName,
                            title = title,
                            text = text,
                            timestamp = System.currentTimeMillis()
                        ))
                        // 只保留最近的通知
                        if (recentNotifications.size > maxNotifications) {
                            recentNotifications.removeAt(recentNotifications.size - 1)
                        }
                    }
                    Log.d(TAG, "通知: [$packageName] $title - $text")
                }
            }
        }
    }
    
    override fun onInterrupt() {
        // 服务中断
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!com.aiautomation.util.ExecPrefs.isStopShortcutEnabled(this)) {
            return super.onKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            val delta = now - lastVolDownAt
            if (lastVolDownAt != 0L && delta in 1..volDoublePressWindowMs) {
                lastVolDownAt = 0L
                Log.w(TAG, "双击音量减 -> 中断任务")
                // 发送停止广播（AutomationService 监听）
                sendBroadcast(Intent(AutomationService.ACTION_STOP))
                // 吞掉第二次按键，避免额外调低音量
                return true
            }
            lastVolDownAt = now
            // 不吞掉第一次，保持正常调音量
            return false
        }
        return super.onKeyEvent(event)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
        try {
            val info = serviceInfo
            Log.d(
                TAG,
                "serviceInfo: capabilities=${info.capabilities}, flags=${info.flags}, eventTypes=${info.eventTypes}, feedbackType=${info.feedbackType}, notificationTimeout=${info.notificationTimeout}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "读取 serviceInfo 失败: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    fun getScreenContent(): String {
        val rootNode = rootInActiveWindow ?: return ""
        return buildString {
            traverseNode(rootNode, 0, this)
        }
    }
    
    private fun traverseNode(node: AccessibilityNodeInfo, depth: Int, builder: StringBuilder) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        
        if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
            builder.append("$indent[${className.substringAfterLast('.')}]")
            if (text.isNotEmpty()) builder.append(" text=\"$text\"")
            if (contentDesc.isNotEmpty()) builder.append(" desc=\"$contentDesc\"")
            if (node.isClickable) builder.append(" [可点击]")
            builder.append("\n")
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { childNode ->
                traverseNode(childNode, depth + 1, builder)
                childNode.recycle()
            }
        }
    }
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeRecursive(rootNode, text)
    }

    /**
     * 给定屏幕坐标，尝试在当前无障碍节点树中找到“包含该点”的最内层可点击节点，并返回其中心点坐标。
     * 注意：这里不执行节点点击，只用于把AI给的坐标吸附到更可靠的点击区域中心。
     */
    fun findClickableCenterAt(x: Int, y: Int): Pair<Int, Int>? {
        val root = rootInActiveWindow ?: return null
        return findClickableCenterRecursive(root, x, y)
    }

    private fun findClickableCenterRecursive(node: AccessibilityNodeInfo, x: Int, y: Int): Pair<Int, Int>? {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 常规情况下子节点一定在父节点范围内，父节点不包含则直接剪枝
        if (!rect.contains(x, y)) return null

        // 先找更内层的子节点（更精确）
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findClickableCenterRecursive(child, x, y)
                child.recycle()
                if (found != null) return found
            }
        }

        // 子节点没有更精确命中，则用当前节点
        return if (node.isClickable) {
            rect.centerX() to rect.centerY()
        } else {
            null
        }
    }
    
    private fun findNodeRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        if (nodeText.contains(text, ignoreCase = true) || 
            contentDesc.contains(text, ignoreCase = true)) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { childNode ->
                val found = findNodeRecursive(childNode, text)
                if (found != null) return found
                childNode.recycle()
            }
        }
        return null
    }
    
    suspend fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    
    suspend fun clickAt(x: Int, y: Int): Boolean = withContext(Dispatchers.Main) {
        val dm = resources.displayMetrics
        val cx = x.coerceIn(1, dm.widthPixels - 2)
        val cy = y.coerceIn(1, dm.heightPixels - 2)
        Log.d(TAG, "[click] 点击坐标: ($x, $y) -> ($cx, $cy), screen=${dm.widthPixels}x${dm.heightPixels}")

        suspend fun dispatchOnce(path: Path, startTime: Long, duration: Long, label: String): Boolean {
            return suspendCancellableCoroutine { continuation ->
                try {
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, startTime, duration))
                        .build()

                    val callback = object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Log.d(TAG, "[click] ✓ 成功 ($label)")
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Log.e(TAG, "[click] ✗ 取消 ($label)")
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }

                    val dispatched = dispatchGesture(gesture, callback, null)
                    Log.d(TAG, "[click] 分发($label): $dispatched")
                    if (!dispatched && continuation.isActive) {
                        Log.e(TAG, "[click] ✗ 分发失败 ($label)")
                        continuation.resume(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[click] 异常 ($label)", e)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }

        // 方案A：AutoX.js 风格（短tap）
        val p1 = Path().apply { moveTo(cx.toFloat(), cy.toFloat()) }
        val ok1 = dispatchOnce(p1, startTime = 100, duration = 1, label = "A")
        if (ok1) return@withContext true

        // 方案B：兜底（更保守）：增加最小位移 + 更长duration，提升命中率
        delay(60)
        val p2 = Path().apply {
            moveTo(cx.toFloat(), cy.toFloat())
            lineTo((cx + 1).toFloat(), (cy + 1).toFloat())
        }
        return@withContext dispatchOnce(p2, startTime = 0, duration = 50, label = "B")
    }
    
    suspend fun longPressAt(x: Int, y: Int, duration: Long = 1000): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val dm = resources.displayMetrics
            val cx = x.coerceIn(1, dm.widthPixels - 2)
            val cy = y.coerceIn(1, dm.heightPixels - 2)
            Log.d(TAG, "[longPress] 长按: ($x,$y) -> ($cx,$cy), 时长=${duration}ms, screen=${dm.widthPixels}x${dm.heightPixels}")
            val path = Path().apply { moveTo(cx.toFloat(), cy.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 100, duration))
                .build()
            
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "[longPress] ✓ 手势执行完成")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "[longPress] ✗ 手势被取消")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
            
            gestureHandler.post {
                val dispatched = dispatchGesture(gesture, callback, null)
                if (!dispatched) {
                    Log.e(TAG, "[longPress] ✗ 手势分发失败")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
            
            continuation.invokeOnCancellation {
                Log.w(TAG, "[longPress] 操作被取消或超时")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[longPress] 异常: ${e.message}", e)
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }
    
    suspend fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        val focusOk = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val setOk = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
        )
        return focusOk && setOk
    }

    /**
     * 坐标驱动输入/清空：按屏幕坐标在当前窗口节点树中定位可编辑控件（按 bounds 命中），然后执行 ACTION_SET_TEXT。
     * 如果找不到可编辑控件，则使用剪贴板粘贴兜底。
     */
    suspend fun setTextAt(x: Int, y: Int, text: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val dm = resources.displayMetrics
            val cx = x.coerceIn(1, dm.widthPixels - 2)
            val cy = y.coerceIn(1, dm.heightPixels - 2)
            Log.d(TAG, "[setText] 坐标: ($x,$y) -> ($cx,$cy), len=${text.length}")

            val node = findEditableNodeAt(cx, cy)
            
            // 方案1：找到可编辑节点，使用 ACTION_SET_TEXT
            if (node != null) {
                val okSet = inputText(node, text)
                if (okSet) {
                    Log.d(TAG, "[setText] ✓ ACTION_SET_TEXT 成功")
                    node.recycle()
                    return@withContext true
                }

                // 尝试剪贴板粘贴
                node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                    }
                )
                kotlinx.coroutines.delay(50)
                
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Boss助手", text))
                
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                kotlinx.coroutines.delay(100)
                
                val pasteOk = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                kotlinx.coroutines.delay(50)
                node.refresh()
                val finalText = node.text?.toString() ?: ""
                val success = pasteOk && finalText == text
                
                Log.d(TAG, "[setText] paste兜底: pasteOk=$pasteOk, 期望='$text', 实际='$finalText', 成功=$success")
                node.recycle()
                if (success) return@withContext true
            }
            
            // 方案2：找不到可编辑节点（如浏览器输入框），使用全局剪贴板粘贴
            Log.d(TAG, "[setText] 尝试全局剪贴板粘贴兜底...")
            return@withContext pasteTextGlobal(text)
            
        } catch (e: Exception) {
            Log.e(TAG, "[setText] 异常", e)
            false
        }
    }
    
    /**
     * 全局剪贴板粘贴：适用于无法找到可编辑节点的情况（如浏览器地址栏）
     * 假设当前焦点已在输入框内
     */
    private suspend fun pasteTextGlobal(text: String): Boolean = withContext(Dispatchers.Main) {
        try {
            // 1. 设置剪贴板内容
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Boss助手", text))
            kotlinx.coroutines.delay(100)
            
            // 2. 尝试找到当前焦点节点并粘贴
            val root = rootInActiveWindow
            if (root != null) {
                val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusedNode != null) {
                    // 先尝试 SET_TEXT
                    val setOk = focusedNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        android.os.Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        }
                    )
                    if (setOk) {
                        Log.d(TAG, "[pasteGlobal] ✓ 焦点节点 SET_TEXT 成功")
                        focusedNode.recycle()
                        return@withContext true
                    }
                    
                    // 再尝试粘贴
                    val pasteOk = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (pasteOk) {
                        Log.d(TAG, "[pasteGlobal] ✓ 焦点节点粘贴成功")
                        focusedNode.recycle()
                        return@withContext true
                    }
                    focusedNode.recycle()
                }
            }
            
            // 3. 尝试遍历所有可编辑节点进行粘贴
            val allEditables = findAllEditableNodes(root)
            for (editNode in allEditables) {
                editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                kotlinx.coroutines.delay(50)
                val pOk = editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                if (pOk) {
                    Log.d(TAG, "[pasteGlobal] ✓ 遍历找到可粘贴节点")
                    allEditables.forEach { it.recycle() }
                    return@withContext true
                }
            }
            allEditables.forEach { it.recycle() }
            
            Log.d(TAG, "[pasteGlobal] 剪贴板已设置，等待用户操作或AI重试")
            return@withContext true // 剪贴板已准备好
            
        } catch (e: Exception) {
            Log.e(TAG, "[pasteGlobal] 异常", e)
            false
        }
    }

    private fun findEditableNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findEditableNodeRecursive(root, x, y)
    }

    private fun findEditableNodeRecursive(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        // 先尝试更内层节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNodeRecursive(child, x, y)
            if (found != null) {
                // found 可能就是 child，本例中不能 recycle child
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }

        return if (isEditableCandidate(node)) node else null
    }

    private fun isEditableCandidate(node: AccessibilityNodeInfo): Boolean {
        // 可见/可用优先
        if (!node.isEnabled) return false
        try {
            if (!node.isVisibleToUser) return false
        } catch (_: Exception) {
            // 某些ROM可能抛异常，忽略
        }

        val cls = node.className?.toString() ?: ""
        val supportsSetText = (node.actions and AccessibilityNodeInfo.ACTION_SET_TEXT) != 0
        val supportsPaste = (node.actions and AccessibilityNodeInfo.ACTION_PASTE) != 0

        return node.isEditable || supportsSetText || supportsPaste || cls.contains("EditText", ignoreCase = true)
    }
    
    /**
     * 查找所有可编辑节点
     */
    private fun findAllEditableNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result
        findAllEditableNodesRecursive(root, result)
        return result
    }
    
    private fun findAllEditableNodesRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (isEditableCandidate(node)) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllEditableNodesRecursive(child, result)
        }
    }
    
    
    /**
     * 滑动操作 - 使用回调确保真正执行完成
     */
    suspend fun swipe(
        startX: Int, 
        startY: Int, 
        endX: Int, 
        endY: Int, 
        duration: Long = 300
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val dm = resources.displayMetrics
            fun clamp(v: Int, min: Int, max: Int) = kotlin.math.max(min, kotlin.math.min(max, v))
            val minX = 1
            val minY = 1
            val maxX = dm.widthPixels - 2
            val maxY = dm.heightPixels - 2
            val sx = clamp(startX, minX, maxX)
            val sy = clamp(startY, minY, maxY)
            val ex = clamp(endX, minX, maxX)
            val ey = clamp(endY, minY, maxY)
            
            Log.d(TAG, "[swipe] 滑动: ($sx,$sy) -> ($ex,$ey), 时长=${duration}ms")
            
            val path = Path().apply {
                moveTo(sx.toFloat(), sy.toFloat())
                lineTo(ex.toFloat(), ey.toFloat())
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 100, duration))
                .build()
            
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "[swipe] ✓ 手势执行完成")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "[swipe] ✗ 手势被取消")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
            
            gestureHandler.post {
                val dispatched = dispatchGesture(gesture, callback, null)
                if (!dispatched) {
                    Log.e(TAG, "[swipe] ✗ 手势分发失败")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                } else {
                    Log.d(TAG, "[swipe] 手势已分发,等待回调确认...")
                }
            }
            
            continuation.invokeOnCancellation {
                Log.w(TAG, "[swipe] 操作被取消或超时")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[swipe] 异常: ${e.message}", e)
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }
    
    /**
     * 返回键
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    /**
     * Home 键
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    /**
     * 最近任务键
     */
    fun performRecent(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    /**
     * 下拉通知栏
     */
    fun performPullNotification(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
    
    /**
     * 下拉快速设置
     */
    fun performPullQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }
    
    /**
     * 清除文本
     */
    suspend fun clearText(node: AccessibilityNodeInfo): Boolean {
        val focusOk = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val setOk = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
        )
        return focusOk && setOk
    }
    
    fun currentTopPackage(): String? {
        return try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
    }
    
    /**
     * 获取最近的通知信息（用于发送给AI）
     */
    fun getRecentNotifications(): List<NotificationInfo> {
        return synchronized(recentNotifications) {
            recentNotifications.toList()
        }
    }
    
    /**
     * 清除通知历史（任务开始时调用）
     */
    fun clearNotifications() {
        synchronized(recentNotifications) {
            recentNotifications.clear()
        }
        Log.d(TAG, "已清除通知历史")
    }

    companion object {
        private const val TAG = "AccessibilityService"
        var instance: MyAccessibilityService? = null
            private set
    }
}
