package com.aiautomation.automation

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.DisplayMetrics
import com.aiautomation.ai.DoubaoApiClient
import com.aiautomation.ai.ImageData
import com.aiautomation.data.model.Action
import com.aiautomation.data.model.ExecutionResult
import com.aiautomation.data.model.Task
import com.aiautomation.data.model.TaskStatus
import com.aiautomation.service.MyAccessibilityService
import com.aiautomation.service.ScreenCaptureService
import com.aiautomation.service.FloatWindowManager
import com.google.gson.Gson
import com.aiautomation.util.StepDelayPrefs
import com.aiautomation.util.Apps
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import com.aiautomation.util.AppLog
import kotlin.math.roundToInt

class TaskManager(
    private val context: Context,
    private val apiClient: DoubaoApiClient,
    private val modelId: String
) {
    private val gson = Gson()
    private var previousResponseId: String? = null
    private var isPaused = false
    private var isStopped = false
    
    /**
     * 停止任务
     */
    fun stop() {
        isStopped = true
        Log.d(TAG, "任务已被手动停止")
        AppLog.d(TAG, "任务已被手动停止")
    }

    // 记录最近一次“坐标点击/长按”的最终像素坐标，用于 input/clear_text 在未提供坐标时兜底
    private var lastTouchPointPx: Pair<Int, Int>? = null
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    
    // 屏幕变化检测
    private var lastScreenshotHash: String? = null
    private var screenNoChangeCount = 0 // 记录屏幕未变化的次数
    
    var progressCallback: ProgressCallback? = null
    
    interface ProgressCallback {
        fun onStatusUpdate(status: String)
        fun onStepCompleted(step: Int, total: Int)
        fun onTaskCompleted(success: Boolean)
        fun onTaskCompletedWithResult(success: Boolean, result: String)
    }
    
    suspend fun executeTask(task: Task): TaskStatus {
        try {
            Log.d(TAG, "========== TaskManager.executeTask START ==========")
            Log.d(TAG, "任务内容: ${task.description}")
            Log.d(TAG, "Model ID: $modelId")
            progressCallback?.onStatusUpdate("开始任务: ${task.description}")

            // 初始化系统提示词
            val systemPrompt = buildSystemPrompt()
            Log.d(TAG, "系统提示词长度: ${systemPrompt.length} chars")

            // 在开始前先回到桌面，确保从统一上下文开始
            MyAccessibilityService.instance?.let {
                Log.d(TAG, "执行预步骤：返回 Home")
                AppLog.d(TAG, "预步骤：Home")
                it.performHome()
                kotlinx.coroutines.delay(800)
            }

            // 记录执行会话
            com.aiautomation.util.ExecRecorder.startSession(task.title)
            
            // 清除之前的通知历史，只关注任务执行过程中的通知
            MyAccessibilityService.instance?.clearNotifications()
            
            // 初始化悬浮窗（在主线程）
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (!FloatWindowManager.isInitialized()) {
                    FloatWindowManager.init(context)
                    Log.d(TAG, "悬浮窗已初始化")
                }
                FloatWindowManager.updateStatus("准备执行")
                FloatWindowManager.show()
                Log.d(TAG, "悬浮窗已显示")
            }

            // 第一次调用：发送任务描述（IO）
            val initialMessage = "$systemPrompt\n\n用户任务: ${task.description}"
            Log.d(TAG, "发送初始消息给 AI...")
            var response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                apiClient.chat(modelId, initialMessage, previousResponseId)
            }
            previousResponseId = response.id
            Log.d(TAG, "初始响应 ID: $previousResponseId")

            var stepCount = 0
            val maxSteps = com.aiautomation.util.ExecPrefs.getMaxSteps(context)
            var consecutiveFailures = 0 // 记录连续失败次数
            val maxFailures = 3 // 最多允许3次连续失败
            var screenChanged = true // 记录屏幕是否变化

            while (stepCount < maxSteps && !isStopped) {
                // 检查暂停状态
                while (FloatWindowManager.isPaused() && !isStopped) {
                    kotlinx.coroutines.delay(500)
                }
                if (isStopped) break
                
                stepCount++
                Log.d(TAG, "---------- 步骤 $stepCount/$maxSteps ----------")
                progressCallback?.onStepCompleted(stepCount, maxSteps)
                progressCallback?.onStatusUpdate("步骤 $stepCount/$maxSteps: 分析屏幕...")
                FloatWindowManager.updateStepCount(stepCount, maxSteps)
                updateFloatingStatus("步骤 $stepCount")

                // 1. 隐藏悬浮窗（截图前）
                Log.d(TAG, "隐藏悬浮窗...")
                AppLog.d(TAG, "步骤 $stepCount: 隐藏悬浮窗并截图")
                hideFloatingWindow()
                kotlinx.coroutines.delay(300) // 等待动画完成

                // 2. 截取屏幕（屏幕录制方式） — IO/Default
                Log.d(TAG, "开始截图...")
                val base64Image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { captureScreenAsBase64() }
                com.aiautomation.util.ExecRecorder.recordStep(
                    step = stepCount,
                    screenshotBase64 = base64Image,
                    aiText = null,
                    actionJson = null,
                    resultSuccess = null,
                    resultMessage = null,
                    topPackage = MyAccessibilityService.instance?.currentTopPackage()
                )
                if (base64Image == null) {
                    showFloatingWindow()
                    Log.e(TAG, "截图失败！")
                    progressCallback?.onStatusUpdate("错误: 截图失败")
                    return TaskStatus.FAILED
                }
                Log.d(TAG, "截图成功: ${base64Image.length} chars")
                AppLog.d(TAG, "截图成功，长度 ${base64Image.length}")
                
                // 检查屏幕是否变化
                val currentHash = base64Image.hashCode().toString()
                screenChanged = (lastScreenshotHash == null || currentHash != lastScreenshotHash)
                if (!screenChanged) {
                    screenNoChangeCount++
                    Log.d(TAG, "屏幕未变化 (第${screenNoChangeCount}次)")
                    AppLog.d(TAG, "⚠️ 屏幕未变化 ${screenNoChangeCount}次")
                } else {
                    screenNoChangeCount = 0
                }
                lastScreenshotHash = currentHash

                // 3. 显示悬浮窗
                Log.d(TAG, "显示悬浮窗...")
                showFloatingWindow()

                // 4. 获取通知信息
                val notifications = MyAccessibilityService.instance?.getRecentNotifications() ?: emptyList()
                val notificationText = if (notifications.isNotEmpty()) {
                    val notifStr = notifications.take(5).joinToString("\n") { notif ->
                        val appName = notif.packageName.substringAfterLast('.')
                        "【${appName}】${notif.title}: ${notif.text}"
                    }
                    "\n\n**最近的通知信息：**\n$notifStr\n"
                } else {
                    ""
                }
                
                // 5. 发送图片给 AI 分析 — IO
                Log.d(TAG, "发送图片给 AI 分析...")
                AppLog.d(TAG, "发送图片给 AI 分析…")
                progressCallback?.onStatusUpdate("步骤 $stepCount/$maxSteps: AI 分析中...")
                val imageData = ImageData.Base64("data:image/jpeg;base64,$base64Image")
                
                // 检查是否陷入死循环（3次未变化直接终止）
                if (screenNoChangeCount >= 3) {
                    Log.e(TAG, "屏幕连续${screenNoChangeCount}次未变化，陷入死循环，终止任务")
                    AppLog.e(TAG, "🔁 死循环检测：屏幕${screenNoChangeCount}次未变化")
                    progressCallback?.onStatusUpdate("错误: 屏幕连续未变化，已陷入死循环")
                    playErrorSound()
                    com.aiautomation.util.ExecRecorder.finishSession(false)
                    return TaskStatus.FAILED
                }
                
                // 根据失败和屏幕变化情况构建提示
                val basePrompt = when {
                    screenNoChangeCount >= 2 -> {
                        "🚨 严重警告：屏幕已经${screenNoChangeCount}次没有变化！你陷入了重复循环！" +
                        "\n⚠️ **下一步必须换完全不同的方法，否则任务将被终止！**" +
                        "\n\n建议的替代方案：" +
                        "\n1. 如果之前在点击 → 立即改为滚动/back键/home键" +
                        "\n2. 如果之前在滚动 → 立即改为点击其他区域/back键" +
                        "\n3. 如果多次尝试无效 → 执行 finish 结束任务，说明原因" +
                        "\n4. 考虑使用浏览器或搜索引擎等通用方法" +
                        "\n\n**请查看聊天历史，了解之前做过什么，不要重复！**"
                    }
                    consecutiveFailures >= 2 -> {
                        "🔴 警告：已经连续失败 $consecutiveFailures 次！必须换完全不同的方法！" +
                        "\n\n建议的方案：" +
                        "\n- 点击失败 → 改用滚动或返回键" +
                        "\n- 滚动无效 → 改用点击或返回上一级" +
                        "\n- 输入失败 → 改用搜索或其他输入框" +
                        "\n- 如果再失败1次 → 执行 finish 结束任务" +
                        "\n\n**查看聊天历史，避免重复之前失败的操作！**"
                    }
                    consecutiveFailures == 1 -> {
                        "🟡 提示：上次操作失败，请调整策略。" +
                        "\n建议：调整坐标位置或尝试其他方法。" +
                        "\n如果再失败，必须换完全不同的方法。"
                    }
                    else -> "分析当前屏幕，决定下一步操作以完成任务。"
                }
                
                // 将通知信息附加到提示中
                val promptText = basePrompt + notificationText + 
                    if (notificationText.isNotEmpty()) {
                        "\n❗重要：注意上面的通知信息，这些信息可能表明操作的状态。" +
                        "例如：\n- 如果看到下载开始的通知，说明下载已经启动，不需要重复点击下载按钮" +
                        "\n- 如果看到下载完成通知，可以进行下一步操作" +
                        "\n- 如果看到错误通知，需要处理错误"
                    } else ""
                
                response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    apiClient.chatWithImage(
                        modelId = modelId,
                        imageData = imageData,
                        text = promptText,
                        previousResponseId = previousResponseId
                    )
                }
                previousResponseId = response.id
                Log.d(TAG, "AI 响应 ID: $previousResponseId")

                val aiResponse = response.getText() ?: throw Exception("AI响应为空")

                Log.d(TAG, "AI 响应长度: ${aiResponse.length} chars")
                AppLog.d(TAG, "AI 响应: ${aiResponse.take(120)}…")
                Log.d(TAG, "AI 响应内容: $aiResponse")
                
                // 显示AI思考过程到悬浮框
                val reasoningText = extractReasoning(aiResponse)
                FloatWindowManager.updateAIReasoning(reasoningText)

                // 5. 解析并执行动作 — Default
                val action = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { parseAction(aiResponse) }
                com.aiautomation.util.ExecRecorder.recordStep(
                    step = stepCount,
                    screenshotBase64 = null,
                    aiText = aiResponse,
                    actionJson = null,
                    resultSuccess = null,
                    resultMessage = null,
                    topPackage = null
                )
                Log.d(TAG, "解析出动作: ${action::class.simpleName}")
                AppLog.d(TAG, "解析动作: ${action::class.simpleName}")
                progressCallback?.onStatusUpdate("步骤 $stepCount/$maxSteps: 执行 ${getActionName(action)}")

                Log.d(TAG, "执行动作: ${getActionName(action)}")
                AppLog.d(TAG, "执行: ${getActionName(action)}")

                // 更新悬浮窗显示当前动作
                val actionName = getActionName(action)
                updateFloatingStatus(actionName)
                
                // 如果动作有坐标，更新悬浮窗位置到附近
                when (action) {
                    is Action.Click -> {
                        action.x?.let { x ->
                            action.y?.let { y ->
                                FloatWindowManager.updatePosition(x, y)
                            }
                        }
                    }
                    is Action.LongPress -> {
                        action.x?.let { x ->
                            action.y?.let { y ->
                                FloatWindowManager.updatePosition(x, y)
                            }
                        }
                    }
                    is Action.Swipe -> {
                        FloatWindowManager.updatePosition(action.startX, action.startY)
                    }
                    is Action.Input -> {
                        action.x?.let { x ->
                            action.y?.let { y ->
                                FloatWindowManager.updatePosition(x, y)
                            }
                        }
                    }
                    else -> {
                        // 其他动作不更新位置
                    }
                }

                // 执行动作时隐藏悬浮窗，避免悬浮窗挡住坐标点/拦截触摸导致“日志成功但画面不动”
                val result = try {
                    hideFloatingWindow()
                    kotlinx.coroutines.delay(80)
                    executeActionWithVerify(action)
                } finally {
                    showFloatingWindow()
                }

                Log.d(TAG, "动作执行结果: success=${result.success}, message=${result.message}")
                com.aiautomation.util.ExecRecorder.recordStep(
                    step = stepCount,
                    screenshotBase64 = null,
                    aiText = null,
                    actionJson = getActionName(action),
                    resultSuccess = result.success,
                    resultMessage = result.message,
                    topPackage = MyAccessibilityService.instance?.currentTopPackage()
                )

                if (!result.success) {
                    consecutiveFailures++
                    Log.e(TAG, "动作执行失败 (第${consecutiveFailures}次): ${result.message}")
                    AppLog.e(TAG, "动作失败 (第${consecutiveFailures}次): ${result.message}")
                    progressCallback?.onStatusUpdate("失败(第${consecutiveFailures}次): ${result.message}")
                    playErrorSound()
                    
                    // 如果连续失败过多，才终止任务
                    if (consecutiveFailures >= maxFailures) {
                        Log.e(TAG, "连续失败${consecutiveFailures}次，终止任务")
                        AppLog.e(TAG, "连续失败${maxFailures}次，任务终止")
                        progressCallback?.onStatusUpdate("错误: 连续失败${maxFailures}次，任务终止")
                        com.aiautomation.util.ExecRecorder.finishSession(false)
                        return TaskStatus.FAILED
                    }
                    
                    // 否则继续下一步，让AI重新思考
                    Log.d(TAG, "将让AI重新思考其他方法...")
                    // 继续下一步骤
                } else {
                    // 成功后重置连续失败计数
                    consecutiveFailures = 0
                    // 屏幕变化后也重置计数
                    if (screenChanged) {
                        screenNoChangeCount = 0
                    }
                }

                if (action is Action.Finish) {
                    val resultText = action.result.takeIf { it.isNotBlank() } ?: "任务执行完成"
                    Log.d(TAG, "任务完成: $resultText")
                    progressCallback?.onStatusUpdate("✓ 任务完成")
                    progressCallback?.onTaskCompleted(true)
                    progressCallback?.onTaskCompletedWithResult(true, resultText)
                    updateFloatingStatus("完成")
                    AppLog.d(TAG, "任务完成: $resultText")
                    playSuccessSound()
                    // 延迟隐藏悬浮窗
                    kotlinx.coroutines.delay(2000)
                    FloatWindowManager.hide()
                    com.aiautomation.util.ExecRecorder.finishSession(true)
                    return TaskStatus.SUCCESS
                }

                // 间隔：从设置读取
                val interval = StepDelayPrefs.getDelayMs(context).coerceIn(300L, 10000L)
                kotlinx.coroutines.delay(interval)
            }

            if (isStopped) {
                Log.w(TAG, "任务被停止")
                progressCallback?.onStatusUpdate("任务已停止")
                progressCallback?.onTaskCompleted(false)
                FloatWindowManager.hide()
                return TaskStatus.FAILED
            }

            Log.w(TAG, "达到最大步数限制")
            progressCallback?.onStatusUpdate("错误: 超过最大步数")
            progressCallback?.onTaskCompleted(false)
            playErrorSound()
            com.aiautomation.util.ExecRecorder.finishSession(false)
            return TaskStatus.FAILED

        } catch (e: Exception) {
            Log.e(TAG, "任务执行异常", e)
            Log.e(TAG, "异常堆栈: ${e.stackTraceToString()}")
            progressCallback?.onStatusUpdate("错误: ${e.message}")
            progressCallback?.onTaskCompleted(false)
            playErrorSound()
            FloatWindowManager.hide()
            com.aiautomation.util.ExecRecorder.finishSession(false)
            return TaskStatus.FAILED
        } finally {
            Log.d(TAG, "========== TaskManager.executeTask END ==========")
        }
    }
    
    /**
     * 隐藏悬浮窗（截图前）
     */
    private fun hideFloatingWindow() {
        FloatWindowManager.hide()
    }
    
    /**
     * 显示悬浮窗（截图后）
     */
    private fun showFloatingWindow() {
        FloatWindowManager.show()
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, displayName: String?): String? {
        return try {
            val name = displayName?.takeIf { it.isNotBlank() } ?: "boss_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Boss助手")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    if (!ok) throw IllegalStateException("bitmap.compress failed")
                } ?: throw IllegalStateException("openOutputStream returned null")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri.toString()
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * 更新悬浮窗状态
     */
    private fun updateFloatingStatus(status: String) {
        try {
            FloatWindowManager.updateStatus(status)
        } catch (e: Exception) {
            Log.e(TAG, "更新悬浮窗状态失败", e)
        }
    }
    
    /**
     * 截取屏幕并转为 Base64
     */
    private fun captureScreenAsBase64(): String? {
        val captureService = ScreenCaptureService.instance
            ?: run {
                Log.e(TAG, "屏幕录制服务未启动")
                return null
            }
        
        return captureService.captureScreenAsBase64(quality = 80)
    }
    
    /**
     * 播放成功提示音
     */
    private fun playSuccessSound() {
        if (!com.aiautomation.util.ExecPrefs.isSoundEnabled(context)) return
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        } catch (e: Exception) {
            Log.e(TAG, "播放声音失败", e)
        }
    }
    
    /**
     * 播放错误提示音
     */
    private fun playErrorSound() {
        if (!com.aiautomation.util.ExecPrefs.isSoundEnabled(context)) return
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 300)
        } catch (e: Exception) {
            Log.e(TAG, "播放声音失败", e)
        }
    }
    
    /**
     * 重置状态（新任务）- 开始新会话
     */
    fun reset() {
        isStopped = false
        isPaused = false
        previousResponseId = null  // 清除上一次会话，开始新对话
        lastScreenshotHash = null
        screenNoChangeCount = 0
        lastTouchPointPx = null
        Log.d(TAG, "Ⓜ️ TaskManager 状态已重置，开始新会话")
    }
    
    private fun getActionName(action: Action): String {
        return when (action) {
            is Action.Click -> "点击"
            is Action.LongPress -> "长按"
            is Action.Input -> "输入文本"
            is Action.Swipe -> "滑动"
            is Action.Scroll -> "滚动${action.direction}"
            is Action.DoubleClick -> "双击"
            is Action.CloseApp -> "关闭应用"
            is Action.ScreenshotSave -> "保存截图"
            is Action.OpenApp -> "打开应用"
            is Action.Back -> "返回"
            is Action.Home -> "Home"
            is Action.Recent -> "最近任务"
            is Action.PullNotification -> "下拉通知栏"
            is Action.PullQuickSettings -> "下拉快速设置"
            is Action.ClearText -> "清空输入"
            is Action.Wait -> "等待"
            is Action.Finish -> "完成"
        }
    }
    
    private fun aiToPx(value: Int, maxPx: Int): Int {
        return (value.toFloat() / 1000f * maxPx.toFloat()).roundToInt()
    }

    private fun mapAiPoint(x: Int, y: Int, dm: DisplayMetrics): Pair<Int, Int> {
        // 豆包视觉模型常输出 0-1000 的相对坐标（与图片宽高映射），这里做统一转换。
        // 如果坐标明显大于 1000，则认为已经是像素坐标，直接使用。
        val looksNormalized = (x in 0..1000) && (y in 0..1000)
        val px = if (looksNormalized) aiToPx(x, dm.widthPixels) else x
        val py = if (looksNormalized) aiToPx(y, dm.heightPixels) else y
        return px.coerceIn(1, dm.widthPixels - 2) to py.coerceIn(1, dm.heightPixels - 2)
    }

    private suspend fun executeActionWithVerify(action: Action): ExecutionResult {
        val service = MyAccessibilityService.instance
            ?: return ExecutionResult(false, "无障碍服务未启用")

        return try {
            when (action) {
                is Action.Click -> {
                    if (action.x != null && action.y != null) {
                        val dm = service.resources.displayMetrics
                        val (px, py) = mapAiPoint(action.x, action.y, dm)
                        val snapped = service.findClickableCenterAt(px, py)
                        val (tx, ty) = snapped ?: (px to py)
                        Log.d(TAG, "[coord] click ai=(${action.x}, ${action.y}) -> px=($px, $py) -> tap=($tx, $ty), screen=${dm.widthPixels}x${dm.heightPixels}")
                        lastTouchPointPx = tx to ty
                        val ok = service.clickAt(tx, ty)
                        ExecutionResult(ok, if (ok) "坐标点击成功 ($tx, $ty)" else "坐标点击失败 ($tx, $ty)")
                    } else {
                        ExecutionResult(false, "点击必须提供坐标")
                    }
                }
                
                is Action.LongPress -> {
                    if (action.x != null && action.y != null) {
                        val dm = service.resources.displayMetrics
                        val (px, py) = mapAiPoint(action.x, action.y, dm)
                        val snapped = service.findClickableCenterAt(px, py)
                        val (tx, ty) = snapped ?: (px to py)
                        Log.d(TAG, "[coord] long_press ai=(${action.x}, ${action.y}) -> px=($px, $py) -> press=($tx, $ty), screen=${dm.widthPixels}x${dm.heightPixels}")
                        lastTouchPointPx = tx to ty
                        val ok = service.longPressAt(tx, ty, action.duration)
                        ExecutionResult(ok, if (ok) "长按成功 ($tx, $ty)" else "长按失败 ($tx, $ty)")
                    } else {
                        ExecutionResult(false, "长按需要坐标")
                    }
                }
                
                is Action.Input -> {
                    val dm = service.resources.displayMetrics
                    val (baseX, baseY) = if (action.x != null && action.y != null) {
                        mapAiPoint(action.x, action.y, dm)
                    } else {
                        lastTouchPointPx ?: return ExecutionResult(false, "输入需要坐标，或先点击输入框后再输入")
                    }

                    Log.d(TAG, "[coord] input base=($baseX, $baseY), textLen=${action.text.length}")
                    // 先用坐标点击确保焦点
                    lastTouchPointPx = baseX to baseY
                    val focusOk = service.clickAt(baseX, baseY)
                    if (!focusOk) return ExecutionResult(false, "输入前点击失败 ($baseX, $baseY)")

                    val ok = service.setTextAt(baseX, baseY, action.text)
                    ExecutionResult(ok, if (ok) "输入成功" else "输入失败")
                }
                
                is Action.Swipe -> {
                    val dm = service.resources.displayMetrics
                    val (sx, sy) = mapAiPoint(action.startX, action.startY, dm)
                    val (ex, ey) = mapAiPoint(action.endX, action.endY, dm)
                    Log.d(TAG, "[coord] swipe ai=(${action.startX}, ${action.startY})->(${action.endX}, ${action.endY}) -> px=($sx, $sy)->($ex, $ey), screen=${dm.widthPixels}x${dm.heightPixels}")
                    val success = service.swipe(
                        sx,
                        sy,
                        ex,
                        ey,
                        action.duration
                    )
                    ExecutionResult(success, if (success) "滑动成功" else "滑动失败")
                }
                
                is Action.DoubleClick -> {
                    val dm = service.resources.displayMetrics
                    val (px, py) = if (action.x != null && action.y != null) {
                        mapAiPoint(action.x, action.y, dm)
                    } else {
                        lastTouchPointPx ?: return ExecutionResult(false, "双击需要坐标，或先点击目标后再双击")
                    }
                    val snapped = service.findClickableCenterAt(px, py)
                    val (tx, ty) = snapped ?: (px to py)
                    Log.d(TAG, "[coord] double_click base=($px,$py) -> tap=($tx,$ty)")
                    lastTouchPointPx = tx to ty
                    val ok1 = service.clickAt(tx, ty)
                    if (!ok1) return ExecutionResult(false, "双击第1次失败")
                    kotlinx.coroutines.delay(action.intervalMs.coerceIn(40, 400))
                    val ok2 = service.clickAt(tx, ty)
                    ExecutionResult(ok2, if (ok2) "双击成功" else "双击第2次失败")
                }

                is Action.CloseApp -> {
                    // 语义：最近任务中滑掉当前应用卡片
                    val dm = context.resources.displayMetrics
                    val w = dm.widthPixels
                    val h = dm.heightPixels
                    val cx = w / 2
                    val sy = (h * 0.65).toInt()
                    val ey = (h * 0.15).toInt()

                    val openRecentsOk = service.performRecent()
                    kotlinx.coroutines.delay(500)
                    val swipeOk = service.swipe(cx, sy, cx, ey, 280)
                    kotlinx.coroutines.delay(250)
                    val homeOk = service.performHome()
                    val ok = openRecentsOk && swipeOk && homeOk
                    ExecutionResult(ok, if (ok) "关闭应用成功" else "关闭应用失败")
                }

                is Action.ScreenshotSave -> {
                    val capture = ScreenCaptureService.instance
                        ?: return ExecutionResult(false, "屏幕录制服务未启动")

                    val bmp = capture.captureScreen() ?: return ExecutionResult(false, "截图失败")
                    val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveBitmapToGallery(bmp, action.name)
                    }
                    bmp.recycle()
                    ExecutionResult(uri != null, if (uri != null) "截图已保存到相册" else "保存截图失败")
                }

                is Action.Scroll -> {
                    // 根据方向计算滚动坐标（加入边距与最小位移，避免越界/零位移）
                    val dm = context.resources.displayMetrics
                    val screenWidth = dm.widthPixels
                    val screenHeight = dm.heightPixels
                    val centerX = screenWidth / 2
                    val centerY = screenHeight / 2
                    val edgeMargin = 120 // 边距
                    val minDelta = 200 // 最小滑动距离
                    val base = action.distance ?: (screenWidth.coerceAtMost(screenHeight) / 2)
                    val delta = base.coerceAtLeast(minDelta)
                    
                    var sx = centerX
                    var sy = centerY
                    var ex = centerX
                    var ey = centerY
                    when (action.direction.lowercase()) {
                        "up" -> { sy = (centerY + delta/2).coerceAtMost(screenHeight - edgeMargin); ey = (centerY - delta/2).coerceAtLeast(edgeMargin) }
                        "down" -> { sy = (centerY - delta/2).coerceAtLeast(edgeMargin); ey = (centerY + delta/2).coerceAtMost(screenHeight - edgeMargin) }
                        "left" -> { sx = (centerX + delta/2).coerceAtMost(screenWidth - edgeMargin); ex = (centerX - delta/2).coerceAtLeast(edgeMargin) }
                        "right" -> { sx = (centerX - delta/2).coerceAtLeast(edgeMargin); ex = (centerX + delta/2).coerceAtMost(screenWidth - edgeMargin) }
                        else -> return ExecutionResult(false, "无效的滚动方向: ${action.direction}")
                    }
                    
                    // 防止起止点相同
                    if (sx == ex && sy == ey) {
                        ex = (ex + 50).coerceAtMost(screenWidth - edgeMargin)
                        ey = (ey + 50).coerceAtMost(screenHeight - edgeMargin)
                    }
                    
                    Log.d(TAG, "执行滚动: ($sx,$sy) -> ($ex,$ey)")
                    AppLog.d(TAG, "执行滚动: ($sx,$sy) -> ($ex,$ey)")
                    val success = service.swipe(sx, sy, ex, ey, 300)
                    Log.d(TAG, "滚动结果: $success")
                    AppLog.d(TAG, "滚动${action.direction}结果: $success")
                    ExecutionResult(success, if (success) "向${action.direction}滚动成功" else "向${action.direction}滚动失败")
                }
                
                is Action.Back -> {
                    val success = service.performBack()
                    ExecutionResult(success, if (success) "返回成功" else "返回失败")
                }
                
                is Action.Home -> {
                    val success = service.performHome()
                    ExecutionResult(success, if (success) "Home成功" else "Home失败")
                }
                
                is Action.Recent -> {
                    val success = service.performRecent()
                    ExecutionResult(success, if (success) "打开最近任务成功" else "打开最近任务失败")
                }

                is Action.OpenApp -> {
                    val list = Apps.listLaunchableApps(context)
                    val targetPkg = action.pkg ?: list.firstOrNull { it.label.contains(action.app.orEmpty(), ignoreCase = true) }?.packageName
                    if (targetPkg != null && Apps.launchApp(context, targetPkg)) {
                        // 等待应用启动后验证顶层包
                        kotlinx.coroutines.delay(1200)
                        val top = service.currentTopPackage()
                        return if (top == targetPkg) {
                            ExecutionResult(true, "已打开应用并验证: ${action.app ?: targetPkg}")
                        } else {
                            AppLog.e(TAG, "open_app 顶层校验失败，top=$top, target=$targetPkg，开始抽屉兜底")
                            val ok = if (com.aiautomation.util.ExecPrefs.isDrawerFallbackEnabled(context))
                                openAppViaDrawerOrSearch(action.app ?: targetPkg, targetPkg, service)
                            else false
                            ExecutionResult(ok, if (ok) "抽屉兜底打开成功" else "打开应用失败(含兜底) (兜底=${com.aiautomation.util.ExecPrefs.isDrawerFallbackEnabled(context)})")
                        }
                    } else {
                        AppLog.e(TAG, "open_app 启动失败，开始抽屉兜底")
                        val ok = if (com.aiautomation.util.ExecPrefs.isDrawerFallbackEnabled(context))
                            openAppViaDrawerOrSearch(action.app ?: "", targetPkg, service)
                        else false
                        return ExecutionResult(ok, if (ok) "抽屉兜底打开成功" else "打开应用失败 (兜底=${com.aiautomation.util.ExecPrefs.isDrawerFallbackEnabled(context)})")
                    }
                }
                
                is Action.PullNotification -> {
                    val success = service.performPullNotification()
                    ExecutionResult(success, if (success) "下拉通知栏成功" else "下拉通知栏失败")
                }
                
                is Action.PullQuickSettings -> {
                    val success = service.performPullQuickSettings()
                    ExecutionResult(success, if (success) "下拉快速设置成功" else "下拉快速设置失败")
                }
                
                is Action.ClearText -> {
                    val dm = service.resources.displayMetrics
                    val (baseX, baseY) = if (action.x != null && action.y != null) {
                        mapAiPoint(action.x, action.y, dm)
                    } else {
                        lastTouchPointPx ?: return ExecutionResult(false, "清空需要坐标，或先点击输入框后再清空")
                    }

                    Log.d(TAG, "[coord] clear_text base=($baseX, $baseY)")
                    lastTouchPointPx = baseX to baseY
                    val focusOk = service.clickAt(baseX, baseY)
                    if (!focusOk) return ExecutionResult(false, "清空前点击失败 ($baseX, $baseY)")

                    val ok = service.setTextAt(baseX, baseY, "")
                    ExecutionResult(ok, if (ok) "清空文本成功" else "清空文本失败")
                }
                
                is Action.Wait -> {
                    delay(action.milliseconds)
                    ExecutionResult(true, "等待 ${action.milliseconds}ms 完成")
                }
                
                is Action.Finish -> {
                    ExecutionResult(true, "任务完成")
                }
            }
        } catch (e: Exception) {
            ExecutionResult(false, "执行异常: ${e.message}")
        }
    }
    
    private fun parseAction(aiResponse: String): Action {
        return try {
            // 尝试从 JSON 中提取动作
            val jsonMatch = Regex("""```json\s*(.+?)\s*```""", RegexOption.DOT_MATCHES_ALL)
                .find(aiResponse)
            
            if (jsonMatch != null) {
                var jsonStr = jsonMatch.groupValues[1].trim()
                
                // 尝试修复常见的JSON格式错误
                // 情况1: {"action":"click","x":100,"75} -> {"action":"click","x":100,"y":75}
                jsonStr = jsonStr.replace(Regex(""""x":(\d+),"(\d+)"""), """"x":$1,"y":$2""")
                
                // 情况2: {"action":"click","x":100,"850"} -> {"action":"click","x":100,"y":850}
                // 匹配带引号的数字
                jsonStr = jsonStr.replace(Regex(""""x":(\d+),"(\d+)""""""), """"x":$1,"y":$2""")
                
                // 情况3: {"action":"click","x":"100","850"} -> {"action":"click","x":100,"y":850}
                // 匹配x和y都被引号包裹的情况
                jsonStr = jsonStr.replace(Regex(""""x":"(\d+)","(\d+)""""""), """"x":$1,"y":$2""")
                
                // 情况4: 移除y字段多余的引号（如 "y":850" -> "y":850）
                jsonStr = jsonStr.replace(Regex(""""y":(\d+)""""""), """"y":$1""")
                
                Log.d(TAG, "解析JSON: $jsonStr")
                val jsonObj = JsonParser.parseString(jsonStr).asJsonObject
                val actionType = jsonObj.get("action")?.asString ?: "unknown"
                
                when (actionType) {
                    "click" -> {
                        val desc = jsonObj.get("element")?.asString ?: ""
                        val x = jsonObj.get("x")?.asInt
                        val y = jsonObj.get("y")?.asInt
                        Action.Click(desc, x, y)
                    }
                    "long_press" -> {
                        val desc = jsonObj.get("element")?.asString ?: ""
                        val x = jsonObj.get("x")?.asInt
                        val y = jsonObj.get("y")?.asInt
                        val duration = jsonObj.get("duration")?.asLong ?: 1000
                        Action.LongPress(desc, x, y, duration)
                    }
                    "input" -> {
                        val desc = jsonObj.get("element")?.asString ?: ""
                        val text = jsonObj.get("text")?.asString ?: ""
                        val x = jsonObj.get("x")?.asInt
                        val y = jsonObj.get("y")?.asInt
                        Action.Input(desc, text, x, y)
                    }
                    "swipe" -> {
                        val startX = jsonObj.get("startX")?.asInt ?: 0
                        val startY = jsonObj.get("startY")?.asInt ?: 0
                        val endX = jsonObj.get("endX")?.asInt ?: 0
                        val endY = jsonObj.get("endY")?.asInt ?: 0
                        val duration = jsonObj.get("duration")?.asLong ?: 300
                        Action.Swipe(startX, startY, endX, endY, duration)
                    }
                    "scroll" -> {
                        val direction = jsonObj.get("direction")?.asString ?: "up"
                        val distance = jsonObj.get("distance")?.asInt
                        Action.Scroll(direction, distance)
                    }
                    "double_click" -> {
                        val x = jsonObj.get("x")?.asInt
                        val y = jsonObj.get("y")?.asInt
                        val interval = jsonObj.get("interval")?.asLong ?: 120
                        Action.DoubleClick(x, y, interval)
                    }
                    "close_app" -> Action.CloseApp
                    "screenshot_save" -> {
                        val name = jsonObj.get("name")?.asString
                        Action.ScreenshotSave(name)
                    }
                    "back" -> Action.Back
                    "home" -> Action.Home
                    "recent" -> Action.Recent
                    "pull_notification" -> Action.PullNotification
                    "pull_quick_settings" -> Action.PullQuickSettings
                    "clear_text" -> {
                        val desc = jsonObj.get("element")?.asString ?: ""
                        val x = jsonObj.get("x")?.asInt
                        val y = jsonObj.get("y")?.asInt
                        Action.ClearText(desc, x, y)
                    }
                    "open_app" -> {
                        val app = jsonObj.get("app")?.asString
                        val pkg = jsonObj.get("package")?.asString
                        Action.OpenApp(app, pkg)
                    }
                    "wait" -> {
                        val ms = jsonObj.get("milliseconds")?.asLong ?: 1000
                        Action.Wait(ms)
                    }
                    "finish" -> {
                        val result = jsonObj.get("result")?.asString ?: ""
                        Action.Finish(result)
                    }
                    else -> Action.Wait(1000)
                }
            } else {
                // 未找到 JSON，默认等待
                Action.Wait(1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析动作失败", e)
            AppLog.e(TAG, "❌ JSON解析错误: ${e.message}")
            // 记录错误的JSON，便于调试
            AppLog.e(TAG, "AI响应: ${aiResponse.take(200)}")
            Action.Wait(1000)
        }
    }
    
    private fun buildSystemPrompt(): String {
        val appPairs = Apps.listLaunchableApps(context, 40).joinToString { "${it.label}(${it.packageName})" }
        return """
You are a GUI agent for Android device. You are given a task and your action history, with screenshots. You need to perform the next action to complete the task.

## Output Format
```
Thought: ...
Action: ...
```

## Action Space
click(x, y) # x,y范围0-1000，(0,0)左上角，(1000,1000)右下角
input(x, y, text) # 在坐标处输入文本
scroll(direction) # direction: up/down/left/right
swipe(startX, startY, endX, endY) # 滑动
open_app(app_name) # 打开应用
back() # 返回
home() # 主页
wait() # 等待页面加载
finished(result) # 任务完成，result说明结果

## JSON Format (Required)
```json
{"action":"click","x":500,"y":500}
{"action":"input","x":500,"y":300,"text":"内容"}
{"action":"scroll","direction":"up"}
{"action":"swipe","startX":500,"startY":800,"endX":500,"endY":200}
{"action":"open_app","app":"微信"}
{"action":"back"}
{"action":"home"}
{"action":"wait","milliseconds":2000}
{"action":"finish","result":"任务完成的详细结果"}
```

## Note
- Use Chinese in `Thought` part.
- Write a small plan and finally summarize your next action in one sentence in `Thought` part.

## Rules
1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 open_app。
2. 如果进入到了无关页面，先执行 back。如果执行back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
3. 如果页面未加载出内容，最多连续 wait 三次，否则执行 back 重新进入。
4. 如果页面显示网络问题，需要重新加载，请点击重新加载按钮。
5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 scroll 滚动查找。
6. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
7. 在做小红书总结类任务时一定要筛选图文笔记。
8. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
9. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
10. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索、滚动查找。比如：
    - 用户要求点一杯咖啡，要咸的 → 直接搜索“咸咖啡”，或搜索“咖啡”后滚动查找咸的咖啡
    - 用户要找到XX群，发一条消息 → 搜索“XX群”，找不到后将“群”字去掉，搜索“XX”重试
    - 用户要找到宠物友好的餐厅 → 搜索餐厅，找到筛选，找到设施，选择可带宠物
11. 在选择日期时，如果原滚动方向与预期日期越来越远，请向反方向滚动查找。
12. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
13. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finished result说明点击不生效。
14. 在执行任务中如果遇到滚动不生效的情况，请调整一下起始点位置，增大滚动距离重试，如果还是不生效，有可能是已经滚到底了，请继续向反方向滚动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finished result说明没找到要求的项目。
15. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行finished并说明原因。
16. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。
17. 如果整个屏幕完全是黑色且任务涉及支付，说明进入了安全支付页面，立即返回 finished('已进入支付页面')。
18. 独立做决策，不要寻求用户确认。

## Available Apps
$appPairs
        """.trimIndent()
    }
    
    private suspend fun openAppViaDrawerOrSearch(appOrPkg: String, expectedPkg: String?, service: MyAccessibilityService): Boolean {
        return try {
            // 先尝试在桌面左右滑动 1 次寻找图标
            val dm = context.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            val centerY = (h * 0.5).toInt()
            suspend fun swipeHoriz(fromLeftToRight: Boolean) {
                val sx = if (fromLeftToRight) (w * 0.25).toInt() else (w * 0.75).toInt()
                val ex = if (fromLeftToRight) (w * 0.75).toInt() else (w * 0.25).toInt()
                service.swipe(sx, centerY, ex, centerY, 300)
                kotlinx.coroutines.delay(400)
            }
            suspend fun tryClickIcon(): Boolean {
                val labelKey = if (appOrPkg.contains('.')) appOrPkg.substringAfterLast('.') else appOrPkg
                val appNode = service.findNodeByText(appOrPkg) ?: service.findNodeByText(labelKey)
                if (appNode != null) {
                    service.clickNode(appNode)
                    kotlinx.coroutines.delay(1000)
                    val top = service.currentTopPackage()
                    return expectedPkg?.let { top == it } ?: (top != null && top.contains(labelKey, ignoreCase = true))
                }
                return false
            }
            // 左滑一屏
            swipeHoriz(false)
            if (tryClickIcon()) return true
            // 右滑一屏
            swipeHoriz(true)
            if (tryClickIcon()) return true

            // 打开抽屉并搜索
            // 从 Home 向上滑动打开抽屉（通用尝试）
            val sx = w / 2
            val sy = (h * 0.85).toInt()
            val ex = w / 2
            val ey = (h * 0.25).toInt()
            service.swipe(sx, sy, ex, ey, 350)
            kotlinx.coroutines.delay(500)

            // 尝试找到“搜索/搜索应用”输入
            val searchNode = service.findNodeByText("搜索")
                ?: service.findNodeByText("Search")
                ?: service.findNodeByText("查找")

            if (searchNode != null) {
                service.clickNode(searchNode)
                kotlinx.coroutines.delay(150)
                // 输入应用名（如果传的是包名，取最后段）
                val text = if (appOrPkg.contains('.')) appOrPkg.substringAfterLast('.') else appOrPkg
                service.inputText(searchNode, text)
                kotlinx.coroutines.delay(600)
            }

            // 再查找目标应用图标（使用 label 或包名片段）
            val labelKey = if (appOrPkg.contains('.')) appOrPkg.substringAfterLast('.') else appOrPkg
            val appNode = service.findNodeByText(appOrPkg) ?: service.findNodeByText(labelKey)
            if (appNode != null) {
                service.clickNode(appNode)
                kotlinx.coroutines.delay(1200)
                val top = service.currentTopPackage()
                return expectedPkg?.let { top == it } ?: (top != null && top.contains(labelKey, ignoreCase = true))
            }
            false
        } catch (e: Exception) {
            AppLog.e(TAG, "抽屉兜底异常: ${e.message}")
            false
        }
    }

    /**
     * 提取AI响应中的思考过程
     */
    private fun extractReasoning(aiResponse: String): String {
        return try {
            // 尝试提取reasoning字段
            val reasoningMatch = Regex(""""reasoning"\s*:\s*"([^"]+)""").find(aiResponse)
            if (reasoningMatch != null) {
                return reasoningMatch.groupValues[1].take(80)
            }
            
            // 如果没有reasoning，尝试提取JSON之前的文本
            val jsonMatch = Regex("""```json""").find(aiResponse)
            if (jsonMatch != null) {
                val beforeJson = aiResponse.substring(0, jsonMatch.range.first).trim()
                if (beforeJson.isNotBlank()) {
                    return beforeJson.take(80)
                }
            }
            
            // 如果都没有，返回AI响应的前80个字符
            aiResponse.take(80)
        } catch (e: Exception) {
            ""
        }
    }
    
    companion object {
        private const val TAG = "TaskManager"
    }
}
