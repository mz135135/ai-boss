package com.aiautomation.ai

import android.util.Base64
import android.util.Log
import com.aiautomation.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class DoubaoApiClient(
    private val apiKey: String = DEFAULT_API_KEY,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        private const val TAG = "DoubaoApiClient"
        // 默认 API 配置（从 BuildConfig 读取，在 api.properties 中配置）
        val DEFAULT_API_KEY: String get() = BuildConfig.DOUBAO_API_KEY
        val DEFAULT_BASE_URL: String get() = BuildConfig.DOUBAO_BASE_URL
        val DEFAULT_MODEL_ID: String get() = BuildConfig.DOUBAO_MODEL_ID
        
        @Volatile
        private var INSTANCE: DoubaoApiClient? = null
        
        fun getInstance(): DoubaoApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DoubaoApiClient().also { INSTANCE = it }
            }
        }
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    // 简单文本输入 - Responses API
    suspend fun chat(
        modelId: String,
        userMessage: String,
        previousResponseId: String? = null
    ): ResponsesResponse = withContext(Dispatchers.IO) {
        val content = listOf(
            mapOf(
                "type" to "input_text",
                "text" to userMessage
            )
        )
        chatWithContent(modelId, content, previousResponseId)
    }
    
    // 图文混排 - Responses API
    suspend fun chatWithContent(
        modelId: String,
        content: List<Map<String, Any>>,
        previousResponseId: String? = null
    ): ResponsesResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/responses"
        
        val requestBody = mutableMapOf<String, Any>(
            "model" to modelId,
            "input" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to content
                )
            ),
            "caching" to mapOf("type" to "enabled"),
            "thinking" to mapOf("type" to "disabled"),
            "store" to true,
            "expire_at" to (System.currentTimeMillis() / 1000 + 86400)
        )
        
        if (previousResponseId != null) {
            requestBody["previous_response_id"] = previousResponseId
        }
        
        val json = gson.toJson(requestBody)
        val body = json.toRequestBody(jsonMediaType)
        
        Log.d(TAG, "请求 URL: $url")
        Log.d(TAG, "请求体长度: ${json.length} chars")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "API调用失败: ${response.code} ${response.message}")
                Log.e(TAG, "Error body: $errorBody")
                throw Exception("API调用失败: ${response.code} ${response.message} $errorBody")
            }
            
            val responseBody = response.body?.string() 
                ?: throw Exception("响应体为空")
            
            Log.d(TAG, "API 响应长度: ${responseBody.length} chars")
            Log.d(TAG, "API 响应内容: $responseBody")
            
            try {
                gson.fromJson(responseBody, ResponsesResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "JSON 解析失败", e)
                Log.e(TAG, "Response body: $responseBody")
                throw e
            }
        }
    }
    
    // 图文理解（图片 + 文本）- Responses API
    suspend fun chatWithImage(
        modelId: String,
        imageData: ImageData,
        text: String,
        previousResponseId: String? = null
    ): ResponsesResponse {
        val content = mutableListOf<Map<String, Any>>()
        
        // 添加图片
        when (imageData) {
            is ImageData.Base64 -> {
                content.add(
                    mapOf(
                        "type" to "input_image",
                        "image_url" to imageData.dataUrl
                    )
                )
            }
            is ImageData.Url -> {
                content.add(
                    mapOf(
                        "type" to "input_image",
                        "image_url" to imageData.url
                    )
                )
            }
        }
        
        // 添加文本
        content.add(
            mapOf(
                "type" to "input_text",
                "text" to text
            )
        )
        
        return chatWithContent(modelId, content, previousResponseId)
    }
}

// 图片数据封装
sealed class ImageData {
    data class Base64(val dataUrl: String) : ImageData()  // data:image/png;base64,xxx
    data class Url(val url: String) : ImageData()  // http://...
    
    companion object {
        // 从文件创建 Base64
        fun fromFile(file: File, mimeType: String = "image/png"): Base64 {
            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            return Base64("data:$mimeType;base64,$base64")
        }
        
        // 从 URL 创建
        fun fromUrl(url: String): Url = Url(url)
    }
}

// Responses API 响应 - output 是数组！
data class ResponsesResponse(
    val id: String,
    val output: List<ResponsesOutputItem>,  // 数组格式
    val usage: Usage?
) {
    // 辅助方法：获取第一个输出的文本
    fun getText(): String? {
        return output.firstOrNull()?.content?.firstOrNull()?.text
    }
}

// output 数组的单个元素
data class ResponsesOutputItem(
    val role: String?,
    val content: List<ResponsesContentPart>?
)

data class ResponsesContentPart(
    val type: String,
    val text: String?
)

data class Usage(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)
