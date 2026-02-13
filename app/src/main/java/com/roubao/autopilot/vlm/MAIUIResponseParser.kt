package com.roubao.autopilot.vlm

import org.json.JSONObject
import timber.log.Timber

/**
 * MAI-UI 响应解析器
 * 解析 MAI-UI 模型的 thinking + tool_call 输出格式
 */
object MAIUIResponseParser {

    private const val SCALE_FACTOR = 999

    /**
     * 解析 MAI-UI 原始文本响应
     */
    fun parseResponse(text: String): MAIUIResponse {
        var processedText = text.trim()

        // 处理 thinking model 输出格式 (</think> instead of </thinking>)
        if (processedText.contains("</think>") && !processedText.contains("</thinking>")) {
            processedText = processedText.replace("</think>", "</thinking>")
            processedText = "<thinking>$processedText"
        }

        // 提取 thinking
        val thinkingRegex = Regex("<thinking>(.*?)</thinking>", RegexOption.DOT_MATCHES_ALL)
        val thinking = thinkingRegex.find(processedText)?.groupValues?.get(1)?.trim() ?: ""

        // 提取 tool_call
        val toolCallRegex = Regex("<tool_call>\\s*(.+?)\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL)
        val toolCallMatch = toolCallRegex.find(processedText)

        var action: MAIUIAction? = null
        if (toolCallMatch != null) {
            try {
                val toolCallJson = JSONObject(toolCallMatch.groupValues[1].trim())
                val arguments = toolCallJson.optJSONObject("arguments")
                if (arguments != null) {
                    action = parseAction(arguments)
                }
            } catch (e: Exception) {
                Timber.e("Failed to parse tool_call: ${e.message}")
            }
        }

        return MAIUIResponse(
            thinking = thinking,
            action = action,
            rawResponse = text
        )
    }

    /**
     * 解析动作 JSON 为 MAIUIAction
     */
    private fun parseAction(arguments: JSONObject): MAIUIAction {
        val actionType = arguments.optString("action", "")

        // 解析坐标 (0-999 归一化)
        val coordinate = arguments.optJSONArray("coordinate")
        var x: Float? = null
        var y: Float? = null
        if (coordinate != null && coordinate.length() >= 2) {
            x = coordinate.getDouble(0).toFloat() / SCALE_FACTOR
            y = coordinate.getDouble(1).toFloat() / SCALE_FACTOR
        }

        // 解析 drag 坐标
        val startCoord = arguments.optJSONArray("start_coordinate")
        val endCoord = arguments.optJSONArray("end_coordinate")
        var startX: Float? = null
        var startY: Float? = null
        var endX: Float? = null
        var endY: Float? = null
        if (startCoord != null && endCoord != null) {
            startX = startCoord.getDouble(0).toFloat() / SCALE_FACTOR
            startY = startCoord.getDouble(1).toFloat() / SCALE_FACTOR
            endX = endCoord.getDouble(0).toFloat() / SCALE_FACTOR
            endY = endCoord.getDouble(1).toFloat() / SCALE_FACTOR
        }

        return MAIUIAction(
            type = actionType,
            x = x,
            y = y,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            text = arguments.optString("text", null),
            button = arguments.optString("button", null),
            direction = arguments.optString("direction", null),
            status = arguments.optString("status", null)
        )
    }
}
