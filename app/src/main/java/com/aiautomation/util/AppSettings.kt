package com.aiautomation.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置管理类
 */
object AppSettings {
    private const val PREFS_NAME = "boss_helper_settings"
    
    // 设置键
    private const val KEY_MAX_STEPS = "max_steps"
    private const val KEY_STEP_INTERVAL = "step_interval_ms"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_SHOW_COORDINATES = "show_coordinates"
    
    // 默认值
    const val DEFAULT_MAX_STEPS = 50
    const val DEFAULT_STEP_INTERVAL = 800L  // 毫秒
    const val DEFAULT_SOUND_ENABLED = true
    const val DEFAULT_SHOW_COORDINATES = false
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // 最大步骤数
    fun getMaxSteps(context: Context): Int {
        return getPrefs(context).getInt(KEY_MAX_STEPS, DEFAULT_MAX_STEPS)
    }
    
    fun setMaxSteps(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_MAX_STEPS, value).apply()
    }
    
    // 步骤间隔时间（毫秒）
    fun getStepInterval(context: Context): Long {
        return getPrefs(context).getLong(KEY_STEP_INTERVAL, DEFAULT_STEP_INTERVAL)
    }
    
    fun setStepInterval(context: Context, value: Long) {
        getPrefs(context).edit().putLong(KEY_STEP_INTERVAL, value).apply()
    }
    
    // 声音提醒开关
    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
    }
    
    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }
    
    // 显示坐标开关
    fun isShowCoordinatesEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_COORDINATES, DEFAULT_SHOW_COORDINATES)
    }
    
    fun setShowCoordinatesEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_COORDINATES, enabled).apply()
    }
}
