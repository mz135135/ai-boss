package com.aiautomation

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiautomation.ui.NewMainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android UI 测试示例
 * 
 * 在设备或模拟器上运行
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(NewMainActivity::class.java)

    @Test
    fun useAppContext() {
        // 验证应用包名
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aiautomation", appContext.packageName)
    }

    @Test
    fun testMainActivityLaunches() {
        // 验证主 Activity 可以成功启动
        activityRule.scenario.onActivity { activity ->
            // 可以在这里添加更多的 UI 验证
            assert(activity != null)
        }
    }

    // 更多 UI 测试可以使用 Espresso 或 Compose Testing 框架
    // 例如：
    // - 测试按钮点击
    // - 测试文本输入
    // - 测试导航流程
}
