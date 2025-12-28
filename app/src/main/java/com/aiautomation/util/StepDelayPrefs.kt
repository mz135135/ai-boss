package com.aiautomation.util

import android.content.Context
import android.content.SharedPreferences

object StepDelayPrefs {
    private const val NAME = "aiautomation_prefs"
    private const val KEY_DELAY_MS = "step_delay_ms"
    private const val DEFAULT_DELAY = 2000L

    private fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getDelayMs(ctx: Context): Long = sp(ctx).getLong(KEY_DELAY_MS, DEFAULT_DELAY)

    fun setDelayMs(ctx: Context, value: Long) { sp(ctx).edit().putLong(KEY_DELAY_MS, value).apply() }
}
