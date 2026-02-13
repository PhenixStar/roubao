package com.roubao.autopilot.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * HTTP 请求工具
 *
 * 用于调用外部 API，如：
 * - 调用 AI 服务
 * - 获取天气信息
 * - 查询数据
 *
 * SSRF 防护：使用自定义 Dns 实现将验证后的 IP 锁定到连接中，
 * 防止 DNS 重绑定 (TOCTOU) 攻击。
 */
class HttpTool : Tool {

    companion object {
        /** Max response body size: 1 MB */
        private const val MAX_RESPONSE_SIZE = 1 * 1024 * 1024
        private val ALLOWED_SCHEMES = setOf("http", "https")
    }

    override val name = "http_request"
    override val displayName = "HTTP 请求"
    override val description = "发送 HTTP 请求调用外部 API"

    override val params = listOf(
        ToolParam("url", "string", "请求 URL", required = true),
        ToolParam("method", "string", "HTTP 方法（GET/POST/PUT/DELETE）",
            required = false, defaultValue = "GET"),
        ToolParam("headers", "object", "请求头（JSON 格式）", required = false),
        ToolParam("body", "string", "请求体（POST/PUT 时使用）", required = false),
        ToolParam("timeout", "int", "超时时间（毫秒）",
            required = false, defaultValue = 30000)
    )

    /**
     * 自定义 DNS 解析器 - 解析并验证 IP，防止 DNS 重绑定攻击。
     * OkHttp 使用此解析器返回的 IP 建立连接，不会再做二次解析。
     */
    private object SsrfSafeDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            for (addr in addresses) {
                if (addr.isLoopbackAddress || addr.isSiteLocalAddress ||
                    addr.isLinkLocalAddress || addr.isAnyLocalAddress
                ) {
                    throw SecurityException(
                        "请求被拒绝: 目标地址 ($hostname -> ${addr.hostAddress}) " +
                            "属于内部/回环/链路本地网络"
                    )
                }
            }
            return addresses
        }
    }

    override suspend fun execute(params: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val urlStr = params["url"] as? String
                ?: return@withContext ToolResult.Error("缺少 url 参数")
            val method = (params["method"] as? String)?.uppercase() ?: "GET"
            val timeout = (params["timeout"] as? Number)?.toInt() ?: 30000
            val body = params["body"] as? String

            @Suppress("UNCHECKED_CAST")
            val headers = params["headers"] as? Map<String, String> ?: emptyMap()

            try {
                val url = java.net.URL(urlStr)

                // --- SSRF 防护: 验证协议 ---
                val scheme = url.protocol?.lowercase()
                if (scheme !in ALLOWED_SCHEMES) {
                    return@withContext ToolResult.Error(
                        "不允许的 URL 协议: $scheme (仅支持 http/https)"
                    )
                }
                if (url.host.isNullOrBlank()) {
                    return@withContext ToolResult.Error("URL 缺少主机名")
                }

                // IP 验证在 SsrfSafeDns.lookup() 中完成，同一 IP 用于连接
                val client = OkHttpClient.Builder()
                    .dns(SsrfSafeDns)
                    .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                    .followRedirects(false)
                    .build()

                val requestBuilder = Request.Builder().url(urlStr)

                // 设置请求头
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

                // 构建请求体
                val requestBody = if (body != null && method in listOf("POST", "PUT")) {
                    val contentType = (headers["Content-Type"] ?: "application/json")
                        .toMediaType()
                    body.toRequestBody(contentType)
                } else null

                requestBuilder.method(method, requestBody)

                val response = client.newCall(requestBuilder.build()).execute()
                val responseCode = response.code

                // 读取响应并截断到 1MB
                val responseText = response.body?.let { responseBody ->
                    val source = responseBody.source()
                    source.request(MAX_RESPONSE_SIZE.toLong() + 1)
                    val buffer = source.buffer
                    val truncated = buffer.size > MAX_RESPONSE_SIZE
                    val text = buffer.readString(
                        minOf(buffer.size, MAX_RESPONSE_SIZE.toLong()),
                        Charsets.UTF_8
                    )
                    responseBody.close()
                    if (truncated) "$text\n... [truncated at 1MB]" else text
                } ?: ""

                if (responseCode >= 400) {
                    return@withContext ToolResult.Error(
                        "HTTP $responseCode: $responseText",
                        code = responseCode
                    )
                }

                ToolResult.Success(
                    data = mapOf("status_code" to responseCode, "body" to responseText),
                    message = "请求成功 (HTTP $responseCode)"
                )
            } catch (e: SecurityException) {
                ToolResult.Error(e.message ?: "SSRF 检测到安全风险")
            } catch (e: Exception) {
                ToolResult.Error("请求失败: ${e.message}")
            }
        }

    /** 简化的 GET 请求 */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): ToolResult {
        return execute(mapOf("url" to url, "method" to "GET", "headers" to headers))
    }

    /** 简化的 POST 请求 */
    suspend fun post(
        url: String, body: String, headers: Map<String, String> = emptyMap()
    ): ToolResult {
        return execute(
            mapOf("url" to url, "method" to "POST", "body" to body, "headers" to headers)
        )
    }

    /** POST JSON 请求 */
    suspend fun postJson(
        url: String, json: JSONObject, headers: Map<String, String> = emptyMap()
    ): ToolResult {
        val allHeaders = headers.toMutableMap()
        allHeaders["Content-Type"] = "application/json"
        return post(url, json.toString(), allHeaders)
    }
}
