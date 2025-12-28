package com.aiautomation.util

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExecRecorder {
    data class Session(val id: String, val dir: File)

    private var current: Session? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun startSession(title: String): Session {
        if (!ExecPrefs.isRecordingEnabled(AppCtx.app)) {
            current = null
            return Session("disabled", File(AppCtx.app.filesDir, "exec_records_disabled"))
        }
        val root = File(AppCtx.app.filesDir, "exec_records").apply { mkdirs() }
        val id = System.currentTimeMillis().toString()
        val dir = File(root, id).apply { mkdirs() }
        File(dir, "meta.json").writeText(
            JSONObject().apply {
                put("id", id)
                put("title", title)
                put("startTime", sdf.format(Date()))
            }.toString()
        )
        current = Session(id, dir)
        return current!!
    }

    fun recordStep(
        step: Int,
        screenshotBase64: String?,
        aiText: String?,
        actionJson: String?,
        resultSuccess: Boolean?,
        resultMessage: String?,
        topPackage: String?
    ) {
        val s = current ?: return
        val stepsFile = File(s.dir, "steps.jsonl")
        val shotPath = if (ExecPrefs.isRecordScreenshots(AppCtx.app)) screenshotBase64?.let { saveImage(s.dir, step, it) } else null
        val obj = JSONObject().apply {
            put("step", step)
            put("time", sdf.format(Date()))
            if (shotPath != null) put("screenshot", shotPath)
            if (aiText != null) put("ai", aiText)
            if (actionJson != null) put("action", actionJson)
            if (resultSuccess != null) put("success", resultSuccess)
            if (resultMessage != null) put("message", resultMessage)
            if (topPackage != null) put("topPackage", topPackage)
        }
        stepsFile.appendText(obj.toString() + "\n")
    }

    fun finishSession(success: Boolean) {
        val s = current ?: return
        val metaFile = File(s.dir, "meta.json")
        val old = try { org.json.JSONObject(metaFile.readText()) } catch (_: Exception) { JSONObject() }
        old.put("endTime", sdf.format(Date()))
        old.put("success", success)
        metaFile.writeText(old.toString())
        current = null
    }

    private fun saveImage(dir: File, step: Int, base64: String): String? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val path = File(dir, String.format(Locale.US, "step_%03d.jpg", step))
            FileOutputStream(path).use { it.write(bytes) }
            path.absolutePath
        } catch (_: Exception) { null }
    }
}