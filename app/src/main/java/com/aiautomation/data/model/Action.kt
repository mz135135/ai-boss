package com.aiautomation.data.model

sealed class Action {
    /**
     * 点击操作
     * @param elementDescription 元素描述（可选，当有坐标时可为空）
     * @param x 点击 X 坐标
     * @param y 点击 Y 坐标
     */
    data class Click(
        val elementDescription: String = "",
        val x: Int? = null,
        val y: Int? = null
    ) : Action()
    
    /**
     * 长按操作
     * @param elementDescription 元素描述
     * @param x 长按 X 坐标
     * @param y 长按 Y 坐标
     * @param duration 长按时长（毫秒）
     */
    data class LongPress(
        val elementDescription: String = "",
        val x: Int? = null,
        val y: Int? = null,
        val duration: Long = 1000
    ) : Action()
    
    /**
     * 输入文本（坐标优先）
     * @param x/y 输入框坐标（推荐提供；不提供则由执行器使用“上一次点击坐标”兜底）
     * @param elementDescription 仅用于描述/日志，不用于查找节点
     * @param text 要输入的文本
     */
    data class Input(
        val elementDescription: String = "",
        val text: String,
        val x: Int? = null,
        val y: Int? = null
    ) : Action()
    
    /**
     * 滑动操作
     * @param startX 起始 X 坐标
     * @param startY 起始 Y 坐标
     * @param endX 结束 X 坐标
     * @param endY 结束 Y 坐标
     * @param duration 滑动时长（毫秒）
     */
    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val duration: Long = 300
    ) : Action()
    
    /**
     * 滚动操作（向上/向下/向左/向右）
     * @param direction 方向: up, down, left, right
     * @param distance 滚动距离（可选，默认半屏）
     */
    data class Scroll(
        val direction: String,  // up, down, left, right
        val distance: Int? = null
    ) : Action()
    
    /**
     * 返回操作（按返回键）
     */
    object Back : Action()
    
    /**
     * Home 键操作（回到桌面）
     */
    object Home : Action()
    
    /**
     * 最近任务键（多任务切换）
     */
    object Recent : Action()
    
    /**
     * 通知栏下拉
     */
    object PullNotification : Action()
    
    /**
     * 快速设置面板下拉
     */
    object PullQuickSettings : Action()
    
    /**
     * 清除文本（清空输入框，坐标优先）
     * @param x/y 输入框坐标（推荐提供；不提供则由执行器使用“上一次点击坐标”兜底）
     * @param elementDescription 仅用于描述/日志，不用于查找节点
     */
    data class ClearText(
        val elementDescription: String = "",
        val x: Int? = null,
        val y: Int? = null
    ) : Action()
    
    /**
     * 等待
     * @param milliseconds 等待时长（毫秒）
     */
    data class Wait(val milliseconds: Long) : Action()
    
    /** 双击（坐标优先） */
    data class DoubleClick(
        val x: Int? = null,
        val y: Int? = null,
        val intervalMs: Long = 120
    ) : Action()

    /** 关闭当前应用：打开最近任务并滑掉当前卡片 */
    object CloseApp : Action()

    /** 保存截图到相册 */
    data class ScreenshotSave(
        val name: String? = null
    ) : Action()

    /** 打开应用 */
    data class OpenApp(
        val app: String? = null,
        val pkg: String? = null
    ) : Action()

    /**
     * 任务完成
     * @param result 任务完成的详细结果描述
     */
    data class Finish(val result: String = "") : Action()
}

data class ExecutionResult(
    val success: Boolean,
    val message: String? = null
)
