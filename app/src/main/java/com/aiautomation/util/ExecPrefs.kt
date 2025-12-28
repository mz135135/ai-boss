package com.aiautomation.util

import android.content.Context
import android.content.SharedPreferences

object ExecPrefs {
    private const val NAME = "aiautomation_prefs"
    private const val KEY_REC_ENABLE = "rec_enable"
    private const val KEY_REC_SHOT = "rec_shot"
    private const val KEY_LOG_MAX_MB = "log_max_mb"
    private const val KEY_DRAWER_FB = "drawer_fb"

    private const val KEY_MAX_STEPS = "max_steps"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_STOP_SHORTCUT_ENABLED = "stop_shortcut_enabled"

    private const val KEY_API_KEY = "api_key"
    private const val KEY_API_BASE_URL = "api_base_url"
    private const val KEY_MODEL_ID = "model_id"

    private fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isRecordingEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_REC_ENABLE, true)
    fun setRecordingEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_REC_ENABLE, v).apply()

    fun isRecordScreenshots(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_REC_SHOT, true)
    fun setRecordScreenshots(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_REC_SHOT, v).apply()

    fun getLogMaxMb(ctx: Context): Int = sp(ctx).getInt(KEY_LOG_MAX_MB, 1).coerceIn(1, 10)
    fun setLogMaxMb(ctx: Context, mb: Int) = sp(ctx).edit().putInt(KEY_LOG_MAX_MB, mb.coerceIn(1, 10)).apply()
    fun getLogMaxBytes(ctx: Context): Long = getLogMaxMb(ctx).toLong() * 1_000_000L

    fun isDrawerFallbackEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_DRAWER_FB, true)
    fun setDrawerFallbackEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_DRAWER_FB, v).apply()

    fun getMaxSteps(ctx: Context): Int = sp(ctx).getInt(KEY_MAX_STEPS, 20).coerceIn(1, 200)
    fun setMaxSteps(ctx: Context, v: Int) = sp(ctx).edit().putInt(KEY_MAX_STEPS, v.coerceIn(1, 200)).apply()

    fun isSoundEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SOUND_ENABLED, true)
    fun setSoundEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_SOUND_ENABLED, v).apply()

    fun isStopShortcutEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_STOP_SHORTCUT_ENABLED, true)
    fun setStopShortcutEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_STOP_SHORTCUT_ENABLED, v).apply()

    fun getApiKey(ctx: Context): String? = sp(ctx).getString(KEY_API_KEY, null)
    fun setApiKey(ctx: Context, v: String?) = sp(ctx).edit().putString(KEY_API_KEY, v).apply()

    fun getApiBaseUrl(ctx: Context): String? = sp(ctx).getString(KEY_API_BASE_URL, null)
    fun setApiBaseUrl(ctx: Context, v: String?) = sp(ctx).edit().putString(KEY_API_BASE_URL, v).apply()

    fun getModelId(ctx: Context): String? = sp(ctx).getString(KEY_MODEL_ID, null)
    fun setModelId(ctx: Context, v: String?) = sp(ctx).edit().putString(KEY_MODEL_ID, v).apply()
}