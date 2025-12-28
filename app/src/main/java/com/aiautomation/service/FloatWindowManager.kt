package com.aiautomation.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.aiautomation.R
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import com.aiautomation.util.AppLog

/**
 * 基于 EasyFloat 的悬浮窗管理器
 * 替代原来的 FloatingWindowService
 */
object FloatWindowManager {
    
    private const val TAG = "FloatWindowManager"
    private const val FLOAT_TAG = "ai_automation_float"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
    
    private var statusText: TextView? = null
    private var statusIndicator: View? = null
    private var stepCountText: TextView? = null
    private var btnStop: ImageButton? = null
    private var btnPause: ImageButton? = null
    private var aiReasoningText: TextView? = null
    
    private var isInitialized = false
    private var isPaused = false
    
    // 暂停状态回调
    var onPauseStateChanged: ((Boolean) -> Unit)? = null
    // 停止回调
    var onStopRequested: (() -> Unit)? = null
    
    /**
     * 初始化悬浮窗
     */
    fun init(context: Context) {
        if (isInitialized) {
            AppLog.d(TAG, "悬浮窗已初始化,跳过")
            return
        }
        
        try {
            EasyFloat.with(context)
                .setTag(FLOAT_TAG)
                .setLayout(R.layout.layout_easy_float) { view ->
                    // 初始化视图引用
                    statusText = view.findViewById(R.id.tvStatus)
                    statusIndicator = view.findViewById(R.id.statusIndicator)
                    stepCountText = view.findViewById(R.id.tvStepCount)
                    btnStop = view.findViewById(R.id.btnStop)
                    btnPause = view.findViewById(R.id.btnPause)
                    aiReasoningText = view.findViewById(R.id.tvAIReasoning)
                    
                    // 设置暂停/继续按钮点击事件
                    btnPause?.setOnClickListener {
                        isPaused = !isPaused
                        updatePauseButton()
                        onPauseStateChanged?.invoke(isPaused)
                        AppLog.d(TAG, if (isPaused) "已暂停" else "已继续")
                    }
                    
                    // 设置停止按钮点击事件
                    btnStop?.setOnClickListener {
                        AppLog.d(TAG, "点击停止按钮")
                        android.util.Log.d(TAG, "用户点击停止按钮")
                        
                        // 调用回调（重要：这会通知 TaskManager 和 UI）
                        onStopRequested?.invoke()
                        
                        // 发送停止广播
                        context.sendBroadcast(Intent(AutomationService.ACTION_STOP))
                        
                        // 更新状态
                        updateStatus("已停止")
                        isPaused = false
                        updatePauseButton()
                        
                        // 延迟隐藏悬浮窗
                        mainHandler.postDelayed({
                            hide()
                        }, 1000)
                    }
                }
                .setShowPattern(ShowPattern.ALL_TIME) // 一直显示
                .setSidePattern(SidePattern.RESULT_HORIZONTAL) // 靠边停靠
                .setDragEnable(true) // 允许拖拽
                .setGravity(android.view.Gravity.END or android.view.Gravity.TOP, 0, 100)
                .show()
            
            isInitialized = true
            AppLog.d(TAG, "悬浮窗初始化成功")
        } catch (e: Exception) {
            AppLog.e(TAG, "悬浮窗初始化失败: ${e.message}")
            android.util.Log.e(TAG, "悬浮窗初始化失败", e)
        }
    }
    
    /**
     * 更新状态文本
     */
    fun updateStatus(status: String) {
        runOnMain {
            try {
                statusText?.text = status
                AppLog.d(TAG, "更新状态: $status")
            } catch (e: Exception) {
                AppLog.e(TAG, "更新状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新悬浮窗位置（跟随操作位置）
     */
    fun updatePosition(x: Int, y: Int) {
        runOnMain {
            try {
                // 计算合适的显示位置（避免遮挡操作位置）
                val offsetX = 100
                val offsetY = -150
                EasyFloat.updateFloat(FLOAT_TAG, x + offsetX, y + offsetY)
                AppLog.d(TAG, "更新位置: ($x, $y) -> (${x + offsetX}, ${y + offsetY})")
            } catch (e: Exception) {
                AppLog.e(TAG, "更新位置失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新步骤数
     */
    fun updateStepCount(current: Int, total: Int) {
        runOnMain {
            try {
                stepCountText?.text = "$current/$total"
            } catch (e: Exception) {
                AppLog.e(TAG, "更新步骤数失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新AI思考过程
     */
    fun updateAIReasoning(reasoning: String) {
        runOnMain {
            try {
                if (reasoning.isNotBlank()) {
                    aiReasoningText?.text = reasoning
                    aiReasoningText?.visibility = View.VISIBLE
                } else {
                    aiReasoningText?.visibility = View.GONE
                }
                AppLog.d(TAG, "更新AI思考: ${reasoning.take(50)}")
            } catch (e: Exception) {
                AppLog.e(TAG, "更新AI思考失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新暂停按钮状态
     */
    private fun updatePauseButton() {
        runOnMain {
            try {
                val icon = if (isPaused) 
                    android.R.drawable.ic_media_play
                else 
                    android.R.drawable.ic_media_pause
                btnPause?.setImageResource(icon)
                
                statusText?.text = if (isPaused) "已暂停" else "执行中"
            } catch (e: Exception) {
                AppLog.e(TAG, "更新暂停按钮失败: ${e.message}")
            }
        }
    }
    
    /**
     * 检查是否暂停
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * 重置暂停状态
     */
    fun resetPauseState() {
        isPaused = false
        updatePauseButton()
    }
    
    /**
     * 隐藏悬浮窗(截图时使用)
     */
    fun hide() {
        runOnMain {
            try {
                EasyFloat.hide(FLOAT_TAG)
            } catch (e: Exception) {
                AppLog.e(TAG, "隐藏悬浮窗失败: ${e.message}")
            }
        }
    }
    
    /**
     * 显示悬浮窗
     */
    fun show() {
        runOnMain {
            try {
                EasyFloat.show(FLOAT_TAG)
            } catch (e: Exception) {
                AppLog.e(TAG, "显示悬浮窗失败: ${e.message}")
            }
        }
    }
    
    /**
     * 销毁悬浮窗
     */
    fun dismiss() {
        runOnMain {
            try {
                EasyFloat.dismiss(FLOAT_TAG)
                isInitialized = false
                statusText = null
                statusIndicator = null
                stepCountText = null
                btnStop = null
                btnPause = null
                aiReasoningText = null
                isPaused = false
                onPauseStateChanged = null
                onStopRequested = null
                AppLog.d(TAG, "悬浮窗已销毁")
            } catch (e: Exception) {
                AppLog.e(TAG, "销毁悬浮窗失败: ${e.message}")
            }
        }
    }
    
    /**
     * 检查悬浮窗是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}
