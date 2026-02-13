package com.roubao.autopilot.vlm

/**
 * MAI-UI 响应
 */
data class MAIUIResponse(
    val thinking: String,
    val action: MAIUIAction?,
    val rawResponse: String
)

/**
 * MAI-UI 动作 (坐标已归一化为 0-1)
 */
data class MAIUIAction(
    val type: String,
    val x: Float? = null,           // 归一化坐标 0-1
    val y: Float? = null,
    val startX: Float? = null,      // drag 起点
    val startY: Float? = null,
    val endX: Float? = null,        // drag 终点
    val endY: Float? = null,
    val text: String? = null,
    val button: String? = null,
    val direction: String? = null,  // swipe 方向: up, down, left, right
    val status: String? = null      // terminate 状态: success, fail
) {
    /**
     * 转换为屏幕像素坐标
     */
    fun toScreenCoordinates(screenWidth: Int, screenHeight: Int): MAIUIAction {
        return copy(
            x = x?.let { (it * screenWidth).coerceIn(0f, (screenWidth - 1).toFloat()) },
            y = y?.let { (it * screenHeight).coerceIn(0f, (screenHeight - 1).toFloat()) },
            startX = startX?.let { (it * screenWidth).coerceIn(0f, (screenWidth - 1).toFloat()) },
            startY = startY?.let { (it * screenHeight).coerceIn(0f, (screenHeight - 1).toFloat()) },
            endX = endX?.let { (it * screenWidth).coerceIn(0f, (screenWidth - 1).toFloat()) },
            endY = endY?.let { (it * screenHeight).coerceIn(0f, (screenHeight - 1).toFloat()) }
        )
    }
}
