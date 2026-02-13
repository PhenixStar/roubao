package com.roubao.autopilot.vlm

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * GUI-Owl API 客户端
 * 专用于阿里云 GUI Agent 服务（非 OpenAI 兼容格式）
 *
 * API 特点：
 * - 专用端点: https://dashscope.aliyuncs.com/api/v2/apps/gui-owl/gui_agent_server
 * - 返回直接的操作指令: Click(x,y), Swipe(x1,y1,x2,y2) 等
 * - 支持 session_id 管理多轮操作
 */
class GUIOwlClient(
    private val apiKey: String,
    private val model: String = "pre-gui_owl_7b",
    private val deviceType: String = "mobile",
    private val thoughtLanguage: String = "chinese"
) : BaseVlmClient() {

    companion object {
        private const val ENDPOINT = "https://dashscope.aliyuncs.com/api/v2/apps/gui-owl/gui_agent_server"
    }

    private val client = createHttpClient()

    // 会话 ID（用于关联多轮操作）
    private var sessionId: String = ""

    /**
     * GUI-Owl 响应结果
     */
    data class GUIOwlResponse(
        val thought: String,      // 思考过程
        val operation: String,    // 操作指令: Click (x, y, x, y) / Swipe (x1, y1, x2, y2) 等
        val explanation: String,  // 操作说明
        val sessionId: String,    // 会话 ID
        val rawResponse: String   // 原始响应
    )

    /**
     * 调用 GUI-Owl 进行界面理解和操作推理
     *
     * @param instruction 用户指令
     * @param imageUrl 截图 URL（支持 http/https 或 data:image/... base64）
     * @param addInfo 额外的操作提示信息
     * @return GUIOwlResponse
     */
    suspend fun predict(
        instruction: String,
        imageUrl: String,
        addInfo: String = ""
    ): Result<GUIOwlResponse> = withContext(Dispatchers.IO) {
        withRetry { attempt ->
            val requestBody = buildRequestBody(instruction, imageUrl, addInfo)

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Timber.d("请求: instruction=$instruction")
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    parseGUIOwlResponse(responseBody)
                } else if (response.code == 429) {
                    val waitMs = calculateRateLimitDelay(response.header("Retry-After"), attempt)
                    Timber.w("Rate limited (429), waiting ${waitMs}ms before retry $attempt/$MAX_RETRIES...")
                    delay(waitMs)
                    Result.failure(Exception("Rate limited (HTTP 429)"))
                } else {
                    Result.failure(Exception("API error: ${response.code} - $responseBody"))
                }
            }
        }
    }

    /**
     * 使用 Bitmap 调用 GUI-Owl
     * 自动将 Bitmap 转换为 base64 data URL
     */
    suspend fun predict(
        instruction: String,
        image: Bitmap,
        addInfo: String = ""
    ): Result<GUIOwlResponse> {
        val imageUrl = bitmapToBase64Url(image)
        return predict(instruction, imageUrl, addInfo)
    }

    /**
     * 解析操作指令字符串，委托给 parseGUIOwlOperation()
     */
    fun parseOperation(operation: String): ParsedAction? = parseGUIOwlOperation(operation)

    /**
     * 重置会话（开始新任务时调用）
     */
    fun resetSession() {
        sessionId = ""
    }

    /**
     * 获取当前会话 ID
     */
    fun getSessionId(): String = sessionId

    /**
     * 构建 GUI-Owl 请求体
     */
    private fun buildRequestBody(instruction: String, imageUrl: String, addInfo: String): JSONObject {
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("image", imageUrl))
            put(JSONObject().put("instruction", instruction))
            put(JSONObject().put("session_id", sessionId))
            put(JSONObject().put("device_type", deviceType))
            put(JSONObject().put("pipeline_type", "agent"))
            put(JSONObject().put("model_name", model))
            put(JSONObject().put("thought_language", thoughtLanguage))
            put(JSONObject().put("param_list", JSONArray().apply {
                put(JSONObject().put("add_info", addInfo))
            }))
        }

        val dataObj = JSONObject().apply { put("messages", messagesArray) }
        val contentArray = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "data")
                put("data", dataObj)
            })
        }
        val inputArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            })
        }

        return JSONObject().apply {
            put("app_id", "gui-owl")
            put("input", inputArray)
        }
    }

    /**
     * 解析 GUI-Owl API 响应
     */
    private fun parseGUIOwlResponse(responseBody: String): Result<GUIOwlResponse> {
        val json = JSONObject(responseBody)

        // 更新 session_id
        val newSessionId = json.optString("session_id", "")
        if (newSessionId.isNotEmpty()) {
            sessionId = newSessionId
        }

        // 解析 output
        val outputArray = json.optJSONArray("output")
        if (outputArray != null && outputArray.length() > 0) {
            val output = outputArray.getJSONObject(0)
            val contentArr = output.optJSONArray("content")

            if (contentArr != null && contentArr.length() > 0) {
                val content = contentArr.getJSONObject(0)
                val data = content.optJSONObject("data")

                if (data != null) {
                    val result = GUIOwlResponse(
                        thought = data.optString("Thought", ""),
                        operation = data.optString("Operation", ""),
                        explanation = data.optString("Explanation", ""),
                        sessionId = sessionId,
                        rawResponse = responseBody
                    )
                    Timber.d("响应: operation=${result.operation}")
                    return Result.success(result)
                }
            }
        }

        return Result.failure(Exception("Invalid response format: $responseBody"))
    }
}
