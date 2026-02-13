package com.roubao.autopilot.agent

import com.roubao.autopilot.controller.DeviceController
import com.roubao.autopilot.vlm.GUIOwlClient
import com.roubao.autopilot.vlm.MAIUIAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * VLM 专用动作执行器 - GUI-Owl 和 MAI-UI 模式
 *
 * 负责将 GUI-Owl / MAI-UI 的解析结果转换为设备控制指令。
 * 与 ActionExecutor (OpenAI 模式) 分离，因为坐标体系和动作类型不同。
 */
class VlmActionExecutor(
    private val controller: DeviceController,
    private val log: (String) -> Unit
) {

    /**
     * 执行 GUI-Owl 解析的动作
     */
    suspend fun executeGUIOwlAction(
        action: GUIOwlClient.ParsedAction,
        screenWidth: Int,
        screenHeight: Int
    ) = withContext(Dispatchers.IO) {
        when (action.type) {
            "click" -> {
                val x = action.x ?: return@withContext
                val y = action.y ?: return@withContext
                log("点击: ($x, $y)")
                controller.tap(x, y)
            }
            "swipe" -> {
                val x1 = action.x ?: return@withContext
                val y1 = action.y ?: return@withContext
                val x2 = action.x2 ?: return@withContext
                val y2 = action.y2 ?: return@withContext
                log("滑动: ($x1, $y1) -> ($x2, $y2)")
                controller.swipe(x1, y1, x2, y2)
            }
            "long_press" -> {
                val x = action.x ?: return@withContext
                val y = action.y ?: return@withContext
                log("长按: ($x, $y)")
                controller.longPress(x, y)
            }
            "type" -> {
                val text = action.text ?: return@withContext
                log("输入: $text")
                controller.type(text)
            }
            "scroll" -> executeDirectionalSwipe(action.text ?: "down", screenWidth, screenHeight)
            "system_button" -> {
                when (action.text?.lowercase()) {
                    "back" -> { log("按返回键"); controller.back() }
                    "home" -> { log("按 Home 键"); controller.home() }
                }
            }
            else -> log("未知动作类型: ${action.type}")
        }
    }

    /**
     * 执行 MAI-UI 解析的动作
     */
    suspend fun executeMAIUIAction(
        action: MAIUIAction,
        screenWidth: Int,
        screenHeight: Int
    ) = withContext(Dispatchers.IO) {
        // 转换归一化坐标到屏幕像素
        val screenAction = action.toScreenCoordinates(screenWidth, screenHeight)

        when (action.type) {
            "click" -> {
                val (x, y) = extractMAIUICoords(screenAction) ?: return@withContext
                log("点击: ($x, $y)")
                controller.tap(x, y)
            }
            "long_press" -> {
                val (x, y) = extractMAIUICoords(screenAction) ?: return@withContext
                log("长按: ($x, $y)")
                controller.longPress(x, y)
            }
            "double_click" -> {
                val (x, y) = extractMAIUICoords(screenAction) ?: return@withContext
                log("双击: ($x, $y)")
                controller.tap(x, y)
                delay(100)
                controller.tap(x, y)
            }
            "type" -> {
                val text = action.text ?: return@withContext
                log("输入: $text")
                controller.type(text)
            }
            "swipe" -> executeMAIUISwipe(action, screenAction, screenWidth, screenHeight)
            "drag" -> {
                val x1 = screenAction.startX?.toInt() ?: return@withContext
                val y1 = screenAction.startY?.toInt() ?: return@withContext
                val x2 = screenAction.endX?.toInt() ?: return@withContext
                val y2 = screenAction.endY?.toInt() ?: return@withContext
                log("拖拽: ($x1, $y1) -> ($x2, $y2)")
                controller.swipe(x1, y1, x2, y2, 500)
            }
            "open" -> {
                val appName = action.text ?: return@withContext
                log("打开应用: $appName")
                controller.openApp(appName)
            }
            "system_button" -> {
                when (action.button?.lowercase()) {
                    "back" -> { log("按返回键"); controller.back() }
                    "home" -> { log("按 Home 键"); controller.home() }
                    "menu" -> { log("按菜单键"); controller.back() }
                    "enter" -> { log("按回车键"); controller.enter() }
                }
            }
            "wait" -> { log("等待..."); delay(2000) }
            else -> log("未知动作类型: ${action.type}")
        }
    }

    // ==================== Private helpers ====================

    /** 提取 MAIUIAction 的屏幕坐标 */
    private fun extractMAIUICoords(screenAction: MAIUIAction): Pair<Int, Int>? {
        val x = screenAction.x?.toInt() ?: return null
        val y = screenAction.y?.toInt() ?: return null
        return Pair(x, y)
    }

    /** 方向式滑动（GUI-Owl scroll） */
    private suspend fun executeDirectionalSwipe(
        direction: String,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        val distance = minOf(screenWidth, screenHeight) / 3
        log("滚动: $direction")
        when (direction) {
            "up" -> controller.swipe(centerX, centerY + distance, centerX, centerY - distance)
            "down" -> controller.swipe(centerX, centerY - distance, centerX, centerY + distance)
            "left" -> controller.swipe(centerX + distance, centerY, centerX - distance, centerY)
            "right" -> controller.swipe(centerX - distance, centerY, centerX + distance, centerY)
        }
    }

    /** MAI-UI swipe 执行 - 支持方向式和坐标式 */
    private suspend fun executeMAIUISwipe(
        action: MAIUIAction,
        screenAction: MAIUIAction,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (action.direction != null) {
            val centerX = screenWidth / 2
            val centerY = screenHeight / 2
            val distance = screenHeight / 3
            log("滑动: ${action.direction}")
            when (action.direction.lowercase()) {
                "up" -> controller.swipe(centerX, centerY + distance, centerX, centerY - distance)
                "down" -> controller.swipe(centerX, centerY - distance, centerX, centerY + distance)
                "left" -> controller.swipe(centerX + distance, centerY, centerX - distance, centerY)
                "right" -> controller.swipe(centerX - distance, centerY, centerX + distance, centerY)
            }
        } else if (screenAction.x != null && screenAction.y != null) {
            // 从指定位置滑动
            val startX = screenAction.x.toInt()
            val startY = screenAction.y.toInt()
            val distance = screenHeight / 3
            val direction = action.direction ?: "up"
            when (direction.lowercase()) {
                "up" -> controller.swipe(startX, startY, startX, startY - distance)
                "down" -> controller.swipe(startX, startY, startX, startY + distance)
                "left" -> controller.swipe(startX, startY, startX - distance, startY)
                "right" -> controller.swipe(startX, startY, startX + distance, startY)
            }
        }
    }
}
