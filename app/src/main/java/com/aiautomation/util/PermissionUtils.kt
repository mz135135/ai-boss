package com.aiautomation.util

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

object PermissionUtils {
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClassName: String): Boolean {
        // serviceClassName e.g. "com.aiautomation.service.MyAccessibilityService"
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabledServices.split(":").any { it.equals(ComponentName(context, serviceClassName).flattenToString(), ignoreCase = true) }
    }
}
