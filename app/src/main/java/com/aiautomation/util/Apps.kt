package com.aiautomation.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object Apps {
    data class AppInfo(val label: String, val packageName: String)

    fun listLaunchableApps(ctx: Context, limit: Int = 40): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = pm.queryIntentActivities(intent, 0)
        return infos.map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
            .take(limit)
            .map { AppInfo(it.first, it.second) }
    }

    fun launchApp(ctx: Context, packageName: String): Boolean {
        val pm = ctx.packageManager
        return try {
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }
}
