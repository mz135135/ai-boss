package com.aiautomation.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Vosk语音识别管理器
 * 支持实时语音转文字
 */
class VoiceRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceRecognizer"
        private const val SAMPLE_RATE = 16000f
        private const val BUFFER_SIZE = 4096
    }
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recognitionJob: Job? = null
    
    // 识别结果回调
    var onPartialResult: ((String) -> Unit)? = null
    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    /**
     * 初始化模型（异步）
     */
    suspend fun initModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查模型文件
            val modelDir = File(context.filesDir, "model")
            
            // 检查模型是否已解压
            if (!modelDir.exists() || !isModelValid(modelDir)) {
                Log.i(TAG, "模型文件不存在或不完整，开始解压...")
                
                // 自定义解压方法
                val success = copyAssets("model-cn", modelDir)
                
                if (!success) {
                    onError?.invoke("模型解压失败")
                    return@withContext false
                }
                
                Log.i(TAG, "模型解压成功")
            } else {
                Log.i(TAG, "使用已有模型")
            }
            
            // 加载模型
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE)
            
            Log.i(TAG, "Vosk模型加载成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败: ${e.message}", e)
            onError?.invoke("初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检查模型是否有效
     */
    private fun isModelValid(modelDir: File): Boolean {
        // 检查必需的模型文件
        val requiredFiles = listOf("am/final.mdl", "conf/mfcc.conf", "graph/HCLG.fst")
        return requiredFiles.all { File(modelDir, it).exists() }
    }
    
    /**
     * 从 assets 复制文件到目标目录
     */
    private fun copyAssets(assetPath: String, targetDir: File): Boolean {
        try {
            val assetManager = context.assets
            val files = assetManager.list(assetPath) ?: return false
            
            if (files.isEmpty()) {
                // 这是一个文件，不是目录
                targetDir.parentFile?.mkdirs()
                assetManager.open(assetPath).use { input ->
                    targetDir.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // 这是一个目录
                targetDir.mkdirs()
                for (file in files) {
                    copyAssets("$assetPath/$file", File(targetDir, file))
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "复制assets失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 开始录音识别
     */
    fun startListening() {
        if (isRecording) {
            Log.w(TAG, "已经在录音中")
            return
        }
        
        if (model == null) {
            onError?.invoke("模型未初始化")
            return
        }
        
        // 每次录音创建新的 recognizer 实例，避免 reset() 导致的 native 崩溃
        try {
            recognizer?.close()
            recognizer = Recognizer(model, SAMPLE_RATE)
            Log.d(TAG, "创建新的 recognizer 实例")
        } catch (e: Exception) {
            Log.e(TAG, "创建 recognizer 失败: ${e.message}")
            onError?.invoke("初始化识别器失败")
            return
        }
        
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE.toInt(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE.toInt(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )
            
            audioRecord?.startRecording()
            isRecording = true
            
            // 开始识别线程
            recognitionJob = CoroutineScope(Dispatchers.IO).launch {
                processAudio()
            }
            
            Log.i(TAG, "开始录音识别")
        } catch (e: Exception) {
            Log.e(TAG, "启动录音失败: ${e.message}", e)
            onError?.invoke("启动录音失败")
        }
    }
    
    /**
     * 停止录音识别
     */
    fun stopListening() {
        isRecording = false
        recognitionJob?.cancel()
        recognitionJob = null
        
        try {
            audioRecord?.apply {
                try {
                    if (state == AudioRecord.STATE_INITIALIZED) {
                        stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "停止录音失败: ${e.message}")
                }
                try {
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "释放AudioRecord失败: ${e.message}")
                }
            }
            audioRecord = null
            
            // 获取最终结果
            recognizer?.apply {
                try {
                    val finalResult = getFinalResult()
                    try {
                        val text = JSONObject(finalResult).getString("text")
                        if (text.isNotEmpty()) {
                            onResult?.invoke(text)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析最终结果失败: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取最终结果失败: ${e.message}")
                }
                
                // reset() 可能导致 native 崩溃，暂时不调用
                // 改用创建新的 recognizer 实例
                try {
                    // reset()
                } catch (e: Exception) {
                    Log.e(TAG, "重置识别器失败: ${e.message}")
                }
            }
            
            Log.i(TAG, "停止录音识别")
        } catch (e: Exception) {
            Log.e(TAG, "stopListening异常: ${e.message}", e)
        }
    }
    
    /**
     * 处理音频流
     */
    private suspend fun processAudio() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(BUFFER_SIZE)
        
        while (isRecording && audioRecord != null) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    recognizer?.let { rec ->
                        if (rec.acceptWaveForm(buffer, bytesRead)) {
                            // 完整识别结果
                            val result = rec.result
                            try {
                                val text = JSONObject(result).getString("text")
                                if (text.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        onResult?.invoke(text)
                                    }
                                } else {
                                    // 空结果
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "解析结果失败: ${e.message}")
                            }
                        } else {
                            // 部分识别结果（实时显示）
                            val partialResult = rec.partialResult
                            try {
                                val partial = JSONObject(partialResult).getString("partial")
                                if (partial.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        onPartialResult?.invoke(partial)
                                    }
                                } else {
                                    // 空结果
                                }
                            } catch (e: Exception) {
                                // 忽略部分结果解析错误
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "读取音频失败: ${e.message}")
                break
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopListening()
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
    }
    
    /**
     * 检查是否正在录音
     */
    fun isListening(): Boolean = isRecording
}
