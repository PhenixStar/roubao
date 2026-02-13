package com.roubao.autopilot.vlm

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * MAI-UI 专用客户端
 * 实现 MAI-UI 的特定 prompt 格式和对话历史管理
 */
class MAIUIClient(
    private val baseUrl: String = "http://localhost:8000/v1",
    private val model: String = "MAI-UI-2B",
    private val historyN: Int = 3  // 保留的历史图片数量
) : BaseVlmClient() {

    private val client = createHttpClient(readTimeout = 120)

    // 对话历史
    private val historyImages = mutableListOf<String>()  // Base64 encoded images
    private val historyResponses = mutableListOf<String>()  // Assistant responses

    // 可用应用列表 (会在运行时更新)
    private var availableApps: List<String> = emptyList()

    /**
     * 设置可用应用列表
     */
    fun setAvailableApps(apps: List<String>) {
        availableApps = apps
    }

    /**
     * 重置对话历史
     */
    fun reset() {
        historyImages.clear()
        historyResponses.clear()
    }

    /**
     * 预测下一步动作
     * @param instruction 用户指令
     * @param screenshot 当前截图
     * @return Result<MAIUIResponse>
     */
    suspend fun predict(
        instruction: String,
        screenshot: Bitmap
    ): Result<MAIUIResponse> = withContext(Dispatchers.IO) {
        // 编码当前截图 (使用 BaseVlmClient 的 bitmapToBase64，返回原始 base64)
        val currentImageBase64 = bitmapToBase64(screenshot)

        withRetry { _ ->
            val messages = buildMessages(instruction, currentImageBase64)

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 2048)
                put("temperature", 0.0)
                put("top_p", 1.0)
            }

            // MAI-UI 本地部署默认 http://，使用 defaultScheme = "http://"
            val request = Request.Builder()
                .url("${normalizeUrl(baseUrl, defaultScheme = "http://")}/chat/completions")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val content = message.getString("content")

                        Timber.d("Raw response: $content")

                        // 解析响应 (委托给 MAIUIResponseParser)
                        val parsed = MAIUIResponseParser.parseResponse(content)

                        // 保存到历史
                        historyImages.add(currentImageBase64)
                        historyResponses.add(content)

                        // 限制历史数量
                        while (historyImages.size > historyN) {
                            historyImages.removeAt(0)
                            historyResponses.removeAt(0)
                        }

                        Result.success(parsed)
                    } else {
                        Result.failure(Exception("No response from model"))
                    }
                } else {
                    Result.failure(Exception("API error: ${response.code} - $responseBody"))
                }
            }
        }
    }

    /**
     * 构建消息列表 (参考 MAI-UI 官方实现)
     */
    private fun buildMessages(instruction: String, currentImageBase64: String): JSONArray {
        val messages = JSONArray()

        // 1. System message
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", getSystemPrompt())
                })
            })
        })

        // 2. User instruction
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", instruction)
                })
            })
        })

        // 3. History (image + assistant response pairs)
        val startIdx = maxOf(0, historyImages.size - (historyN - 1))
        for (i in startIdx until historyImages.size) {
            messages.put(buildImageMessage(historyImages[i]))
            messages.put(buildAssistantMessage(historyResponses[i]))
        }

        // 4. Current image
        messages.put(buildImageMessage(currentImageBase64))
        return messages
    }

    private fun buildImageMessage(base64: String) = JSONObject().apply {
        put("role", "user")
        put("content", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64")
                })
            })
        })
    }

    private fun buildAssistantMessage(text: String) = JSONObject().apply {
        put("role", "assistant")
        put("content", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            })
        })
    }

    /**
     * 系统提示词 (参考 MAI-UI 官方实现)
     */
    private fun getSystemPrompt(): String = """
You are a GUI agent. You are given a task and your action history, with screenshots. You need to perform the next action to complete the task.

## Output Format
For each function call, return the thinking process in <thinking> </thinking> tags, and a json object with function name and arguments within <tool_call></tool_call> XML tags:
```
<thinking>
...
</thinking>
<tool_call>
{"name": "mobile_use", "arguments": <args-json-object>}
</tool_call>
```

## Action Space

{"action": "click", "coordinate": [x, y]}
{"action": "long_press", "coordinate": [x, y]}
{"action": "type", "text": ""}
{"action": "swipe", "direction": "up or down or left or right", "coordinate": [x, y]}
{"action": "open", "text": "app_name"}
{"action": "drag", "start_coordinate": [x1, y1], "end_coordinate": [x2, y2]}
{"action": "system_button", "button": "button_name"}
{"action": "wait"}
{"action": "terminate", "status": "success or fail"}
{"action": "answer", "text": "xxx"}
{"action": "ask_user", "text": "xxx"}

## Note
- Write a small plan and finally summarize your next action (with its target element) in one sentence in <thinking></thinking> part.
- Available Apps: `${if (availableApps.isNotEmpty()) availableApps.toString() else "[请通过open动作打开应用]"}`.
- You should use the `open` action to open the app as possible as you can, because it is the fast way to open the app.
- You must follow the Action Space strictly, and return the correct json object within <thinking> </thinking> and <tool_call></tool_call> XML tags.
    """.trimIndent()
}
