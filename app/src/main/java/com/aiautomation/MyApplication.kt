package com.aiautomation

import android.app.Application
import com.aiautomation.util.AppCtx

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化全局应用上下文
        AppCtx.init(this)
    }
}
