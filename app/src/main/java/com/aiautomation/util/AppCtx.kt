package com.aiautomation.util

import android.content.Context

object AppCtx {
    @Volatile
    lateinit var app: Context
    fun init(context: Context) {
        app = context.applicationContext
    }
}