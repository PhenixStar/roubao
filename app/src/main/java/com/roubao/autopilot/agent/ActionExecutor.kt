package com.roubao.autopilot.agent

import com.roubao.autopilot.controller.AppScanner
import com.roubao.autopilot.controller.DeviceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 动作执行器 - OpenAI 兼容模式
 *
 * 负责执行 Agent 在 OpenAI 兼容模式下输出的 Action 动作指令，
 * 包括坐标映射（Qwen-VL 0-999 相对坐标 → 屏幕绝对像素）。
 */
class ActionExecutor(
    private val controller: DeviceController,
    private val appScanner: AppScanner,
    private val log: (String) -> Unit
) {

    /**
     * 坐标映射 - 支持相对坐标和绝对坐标
     *
     * 坐标格式判断:
     * - 0-999: Qwen-VL 相对坐标 (0-999 映射到屏幕)
     * - >= 1000: 绝对像素坐标，直接使用
     *
     * @param value 模型输出的坐标值
     * @param screenMax 屏幕实际尺寸
     */
    fun mapCoordinate(value: Int, screenMax: Int): Int {
        return if (value < 1000) {
            // 相对坐标 (0-999) -> 绝对像素
            (value * screenMax / 999)
        } else {
            // 绝对坐标，限制在屏幕范围内
            value.coerceAtMost(screenMax)
        }
    }

    /**
     * 执行具体动作 - OpenAI 兼容模式 (在 IO 线程执行，避免 ANR)
     */
    suspend fun executeAction(action: Action, infoPool: InfoPool) = withContext(Dispatchers.IO) {
        // 动态获取屏幕尺寸（处理横竖屏切换）
        val (screenWidth, screenHeight) = controller.getScreenSize()

        when (action.type) {
            "click" -> {
                val (x, y) = extractCoordinates(action) ?: return@withContext
                controller.tap(mapCoordinate(x, screenWidth), mapCoordinate(y, screenHeight))
            }
            "double_tap" -> {
                val (x, y) = extractCoordinates(action) ?: return@withContext
                controller.doubleTap(mapCoordinate(x, screenWidth), mapCoordinate(y, screenHeight))
            }
            "long_press" -> {
                val (x, y) = extractCoordinates(action) ?: return@withContext
                controller.longPress(mapCoordinate(x, screenWidth), mapCoordinate(y, screenHeight))
            }
            "swipe" -> executeSwipe(action, screenWidth, screenHeight)
            "type" -> action.text?.let { controller.type(it) }
            "system_button" -> {
                when (action.button) {
                    "Back", "back" -> controller.back()
                    "Home", "home" -> controller.home()
                    "Enter", "enter" -> controller.enter()
                    else -> log("未知系统按钮: ${action.button}")
                }
            }
            "open_app" -> {
                action.text?.let { appName ->
                    // 智能匹配包名 (客户端模糊搜索，省 token)
                    val packageName = appScanner.findPackage(appName)
                    if (packageName != null) {
                        log("找到应用: $appName -> $packageName")
                        controller.openApp(packageName)
                    } else {
                        log("未找到应用: $appName，尝试直接打开")
                        controller.openApp(appName)
                    }
                }
            }
            "wait" -> {
                val duration = (action.duration ?: 3).coerceIn(1, 10)
                log("等待 ${duration} 秒...")
                delay(duration * 1000L)
            }
            "take_over" -> {
                val message = action.message ?: "请完成操作后点击继续"
                log("🖐 人机协作: $message")
                withContext(Dispatchers.Main) {
                    AgentLoopHelper.waitForUserTakeOver(message)
                }
                log("✅ 用户已完成，继续执行")
            }
            else -> log("未知动作类型: ${action.type}")
        }
    }

    // ==================== Private helpers ====================

    /** 提取并验证 Action 坐标，null 坐标时打印警告并返回 null */
    private fun extractCoordinates(action: Action): Pair<Int, Int>? {
        val x = action.x ?: run {
            log("Warning: null X coordinate, skipping action")
            return null
        }
        val y = action.y ?: run {
            log("Warning: null Y coordinate, skipping action")
            return null
        }
        return Pair(x, y)
    }

    /** OpenAI 模式 swipe 执行 - 支持方向和坐标两种方式 */
    private suspend fun executeSwipe(action: Action, screenWidth: Int, screenHeight: Int) {
        if (action.direction != null) {
            // 方向方式 (MAI-UI 格式)
            val centerX = action.x?.let { mapCoordinate(it, screenWidth) } ?: (screenWidth / 2)
            val centerY = action.y?.let { mapCoordinate(it, screenHeight) } ?: (screenHeight / 2)
            val distance = minOf(screenWidth, screenHeight) / 3
            when (action.direction.lowercase()) {
                "up" -> controller.swipe(centerX, centerY + distance, centerX, centerY - distance)
                "down" -> controller.swipe(centerX, centerY - distance, centerX, centerY + distance)
                "left" -> controller.swipe(centerX + distance, centerY, centerX - distance, centerY)
                "right" -> controller.swipe(centerX - distance, centerY, centerX + distance, centerY)
                else -> log("未知滑动方向: ${action.direction}")
            }
        } else {
            // 坐标方式
            val x1 = mapCoordinate(action.x ?: 0, screenWidth)
            val y1 = mapCoordinate(action.y ?: 0, screenHeight)
            val x2 = mapCoordinate(action.x2 ?: 0, screenWidth)
            val y2 = mapCoordinate(action.y2 ?: 0, screenHeight)
            controller.swipe(x1, y1, x2, y2)
        }
    }
}
