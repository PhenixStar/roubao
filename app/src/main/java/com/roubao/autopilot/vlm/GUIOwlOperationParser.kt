package com.roubao.autopilot.vlm

import timber.log.Timber

/**
 * GUI-Owl 解析操作指令为 Action
 */
data class ParsedAction(
    val type: String,  // click, swipe, type, etc.
    val x: Int? = null,
    val y: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val text: String? = null
)

/**
 * 解析 GUI-Owl 操作指令字符串为 ParsedAction
 *
 * 支持的格式:
 * - Click (x, y, x, y) 或 Click (x, y)
 * - Swipe (x1, y1, x2, y2)
 * - Type (text) 或 Input (text)
 * - Long_press (x, y) 或 LongPress (x, y)
 * - Scroll (direction) 或 Scroll_down / Scroll_up
 * - Back / Home
 * - FINISH / DONE / COMPLETE
 */
fun parseGUIOwlOperation(operation: String): ParsedAction? {
    val trimmed = operation.trim()

    // Click (x, y, x, y) 或 Click (x, y)
    val clickPattern = Regex("""Click\s*\(\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*\d+\s*,\s*\d+)?\s*\)""", RegexOption.IGNORE_CASE)
    clickPattern.find(trimmed)?.let { match ->
        val x = match.groupValues[1].toIntOrNull() ?: return null
        val y = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedAction(type = "click", x = x, y = y)
    }

    // Swipe (x1, y1, x2, y2)
    val swipePattern = Regex("""Swipe\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
    swipePattern.find(trimmed)?.let { match ->
        val x1 = match.groupValues[1].toIntOrNull() ?: return null
        val y1 = match.groupValues[2].toIntOrNull() ?: return null
        val x2 = match.groupValues[3].toIntOrNull() ?: return null
        val y2 = match.groupValues[4].toIntOrNull() ?: return null
        return ParsedAction(type = "swipe", x = x1, y = y1, x2 = x2, y2 = y2)
    }

    // Long_press (x, y) 或 LongPress (x, y)
    val longPressPattern = Regex("""Long[_\s]?[Pp]ress\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
    longPressPattern.find(trimmed)?.let { match ->
        val x = match.groupValues[1].toIntOrNull() ?: return null
        val y = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedAction(type = "long_press", x = x, y = y)
    }

    // Type (text) 或 Input (text)
    val typePattern = Regex("""(?:Type|Input)\s*\(\s*["\']?(.+?)["\']?\s*\)""", RegexOption.IGNORE_CASE)
    typePattern.find(trimmed)?.let { match ->
        val text = match.groupValues[1]
        return ParsedAction(type = "type", text = text)
    }

    // Scroll (direction) 或 Scroll_down / Scroll_up
    val scrollPattern = Regex("""Scroll[_\s]?(up|down|left|right)?""", RegexOption.IGNORE_CASE)
    scrollPattern.find(trimmed)?.let { match ->
        val direction = match.groupValues.getOrNull(1)?.lowercase() ?: "down"
        return ParsedAction(type = "scroll", text = direction)
    }

    // Back
    if (trimmed.contains("Back", ignoreCase = true)) {
        return ParsedAction(type = "system_button", text = "Back")
    }

    // Home
    if (trimmed.contains("Home", ignoreCase = true)) {
        return ParsedAction(type = "system_button", text = "Home")
    }

    // FINISH / DONE / COMPLETE
    if (trimmed.contains(Regex("FINISH|DONE|COMPLETE|Finished", RegexOption.IGNORE_CASE))) {
        return ParsedAction(type = "finish")
    }

    Timber.w("无法解析操作: $operation")
    return null
}
