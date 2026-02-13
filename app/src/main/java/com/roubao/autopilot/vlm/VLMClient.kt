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
 * VLM (Vision Language Model) API 客户端
 * 支持 OpenAI 兼容接口 (GPT-4V, Qwen-VL, Claude, etc.)
 */
class VLMClient(
    private val apiKey: String,
    baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4-vision-preview"
) : BaseVlmClient() {

    // 规范化 URL：自动添加 https:// 前缀，移除末尾斜杠
    private val baseUrl: String = normalizeUrl(baseUrl)

    private val client = createHttpClient()

    companion object {
        /** Shared lightweight client for fetchModels() calls */
        private val fetchClient = createHttpClient(
            connectTimeout = 10,
            readTimeout = 10,
            maxIdleConnections = 2
        )

        /**
         * 从 API 获取可用模型列表
         * @param baseUrl API 基础地址
         * @param apiKey API 密钥
         * @return 模型 ID 列表
         */
        suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
            // 验证 baseUrl 是否为空
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(Exception("Base URL 不能为空"))
            }

            // 清理 URL，确保正确拼接
            val cleanBaseUrl = normalizeUrl(baseUrl.removeSuffix("/chat/completions"))

            val request = try {
                Request.Builder()
                    .url("$cleanBaseUrl/models")
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .get()
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext Result.failure(Exception("Base URL 格式无效: ${e.message}"))
            }

            try {
                fetchClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBody)
                        val data = json.optJSONArray("data") ?: JSONArray()
                        val models = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i)
                            if (item != null) {
                                val id = item.optString("id", "").trim()
                                if (id.isNotEmpty()) {
                                    models.add(id)
                                }
                            }
                        }
                        Result.success(models)
                    } else {
                        Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 调用 VLM 进行多模态推理 (带重试)
     */
    suspend fun predict(
        prompt: String,
        images: List<Bitmap> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        // 预先编码图片 (避免重试时重复编码)
        val encodedImages = images.map { bitmapToBase64Url(it) }

        withRetry { attempt ->
            val content = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })
                encodedImages.forEach { imageUrl ->
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", imageUrl)
                        })
                    })
                }
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 4096)
                put("temperature", 0.0)
                put("top_p", 0.85)
                put("frequency_penalty", 0.2)  // 减少重复输出
            }

            executeChatRequest(requestBody, attempt)
        }
    }

    /**
     * 调用 VLM 进行多模态推理 (使用完整对话历史)
     * @param messagesJson OpenAI 兼容的 messages JSON 数组
     */
    suspend fun predictWithContext(
        messagesJson: JSONArray
    ): Result<String> = withContext(Dispatchers.IO) {
        withRetry { attempt ->
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesJson)
                put("max_tokens", 4096)
                put("temperature", 0.0)
            }

            executeChatRequest(requestBody, attempt)
        }
    }

    /**
     * 执行 chat/completions 请求并解析响应
     * 处理 429 限流等待和标准 OpenAI 响应格式
     */
    private suspend fun executeChatRequest(
        requestBody: JSONObject,
        attempt: Int
    ): Result<String> {
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val choices = json.getJSONArray("choices")
                return if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    Result.success(message.getString("content"))
                } else {
                    Result.failure(Exception("No response from model"))
                }
            } else if (response.code == 429) {
                val waitMs = calculateRateLimitDelay(response.header("Retry-After"), attempt)
                Timber.w("Rate limited (429), waiting ${waitMs}ms before retry $attempt/$MAX_RETRIES...")
                delay(waitMs)
                return Result.failure(Exception("Rate limited (HTTP 429)"))
            } else {
                return Result.failure(Exception("API error: ${response.code} - $responseBody"))
            }
        }
    }
}
