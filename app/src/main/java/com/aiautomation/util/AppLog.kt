package com.aiautomation.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLog {
    data class Entry(val ts: Long, val tag: String, val level: String, val msg: String)
    private val logs = CopyOnWriteArrayList<Entry>()
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private const val LOG_FILE = "applog.txt"
    // 动态从偏好设置读取日志大小上限

    fun d(tag: String, msg: String) = add(tag, "D", msg)
    fun e(tag: String, msg: String) = add(tag, "E", msg)

    @Synchronized
    private fun add(tag: String, level: String, msg: String) {
        val entry = Entry(System.currentTimeMillis(), tag, level, msg)
        logs.add(entry)
        if (logs.size > 2000) logs.removeAt(0)
        // 追加到文件
        try {
            val f = java.io.File(AppCtx.app.filesDir, LOG_FILE)
            rotateIfNeeded(f)
            f.appendText("${sdf.format(Date(entry.ts))} ${entry.level}/$tag: ${entry.msg}\n")
        } catch (_: Exception) {}
    }

    fun all(): List<String> {
        return try {
            val f = java.io.File(AppCtx.app.filesDir, LOG_FILE)
            if (f.exists()) f.readLines() else logs.map { e -> "${sdf.format(Date(e.ts))} ${e.level}/${e.tag}: ${e.msg}" }
        } catch (_: Exception) {
            logs.map { e -> "${sdf.format(Date(e.ts))} ${e.level}/${e.tag}: ${e.msg}" }
        }
    }

    fun clear() {
        logs.clear()
        try { java.io.File(AppCtx.app.filesDir, LOG_FILE).delete() } catch (_: Exception) {}
    }

    private fun rotateIfNeeded(f: java.io.File) {
        val max = try { ExecPrefs.getLogMaxBytes(AppCtx.app) } catch (_: Exception) { 1_000_000L }
        if (f.exists() && f.length() > max) {
            val bak = java.io.File(f.parentFile, "applog.prev.txt")
            try { if (bak.exists()) bak.delete() } catch (_: Exception) {}
            try { f.copyTo(bak, overwrite = true) } catch (_: Exception) {}
            try { f.writeText("") } catch (_: Exception) {}
        }
    }
}
