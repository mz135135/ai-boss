package com.aiautomation

import com.aiautomation.ai.DoubaoApiClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 豆包 API 客户端单元测试
 */
class DoubaoApiClientTest {

    private lateinit var apiClient: DoubaoApiClient

    @Before
    fun setup() {
        // 使用测试配置初始化客户端
        apiClient = DoubaoApiClient(
            apiKey = "test_api_key",
            baseUrl = "https://test.example.com"
        )
    }

    @Test
    fun `test default configuration is loaded from BuildConfig`() {
        // 验证默认配置是否从 BuildConfig 加载
        assertNotNull(DoubaoApiClient.DEFAULT_API_KEY)
        assertNotNull(DoubaoApiClient.DEFAULT_BASE_URL)
        assertNotNull(DoubaoApiClient.DEFAULT_MODEL_ID)
    }

    @Test
    fun `test singleton instance is created`() {
        // 验证单例模式
        val instance1 = DoubaoApiClient.getInstance()
        val instance2 = DoubaoApiClient.getInstance()
        
        assertSame(instance1, instance2)
    }

    // 注意：实际的 API 调用测试需要 mock 网络请求
    // 建议使用 MockWebServer 或 Mockito 进行网络层测试
}
