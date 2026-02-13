package com.roubao.autopilot.vlm

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * VLM 客户端共享基类
 * 提供 OkHttp 客户端创建、图片编码、URL 规范化、带重试的请求执行等通用功能
 */
abstract class BaseVlmClient {

    companion object {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1000L

        /**
         * 创建 OkHttpClient 实例
         * @param connectTimeout 连接超时(秒)
         * @param readTimeout 读取超时(秒)
         * @param writeTimeout 写入超时(秒)
         * @param maxIdleConnections 连接池空闲连接数
         */
        fun createHttpClient(
            connectTimeout: Long = 30,
            readTimeout: Long = 90,
            writeTimeout: Long = 60,
            maxIdleConnections: Int = 5
        ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(maxIdleConnections, 1, TimeUnit.MINUTES))
            .build()

        /**
         * Bitmap 转 Base64 字符串（不含 data URL 前缀）
         * @param bitmap 待编码图片
         * @param quality JPEG 压缩质量 (0-100)
         */
        fun bitmapToBase64(bitmap: Bitmap, quality: Int = 70): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            Timber.d("图片压缩: ${bitmap.width}x${bitmap.height}, ${bytes.size / 1024}KB")
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        /**
         * Bitmap 转 Base64 Data URL (data:image/jpeg;base64,...)
         * 保持原始分辨率以确保坐标准确
         */
        fun bitmapToBase64Url(bitmap: Bitmap, quality: Int = 70): String {
            return "data:image/jpeg;base64,${bitmapToBase64(bitmap, quality)}"
        }

        /**
         * 规范化 URL：自动添加协议前缀，移除末尾斜杠
         * @param url 原始 URL
         * @param defaultScheme 缺省协议前缀 ("https://" 或 "http://")
         */
        fun normalizeUrl(url: String, defaultScheme: String = "https://"): String {
            var normalized = url.trim().removeSuffix("/")
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "$defaultScheme$normalized"
            }
            return normalized
        }
    }

    /**
     * 带重试和退避的请求执行器
     * 自动处理 DNS 解析失败、超时、IO 错误等可重试异常
     *
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 基础重试延迟(ms)，实际延迟 = retryDelayMs * attempt
     * @param block 每次尝试执行的挂起函数，接收当前 attempt (从 1 开始)
     * @return 成功结果或最后一次异常
     */
    protected suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRIES,
        retryDelayMs: Long = RETRY_DELAY_MS,
        block: suspend (attempt: Int) -> Result<T>
    ): Result<T> {
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                val result = block(attempt)
                if (result.isSuccess) return result
                // block 返回 failure，提取异常
                lastException = result.exceptionOrNull() as? Exception
                    ?: Exception(result.exceptionOrNull()?.message ?: "Unknown error")
            } catch (e: UnknownHostException) {
                Timber.w("DNS 解析失败，重试 $attempt/$maxRetries...")
                lastException = e
            } catch (e: java.net.SocketTimeoutException) {
                Timber.w("请求超时，重试 $attempt/$maxRetries...")
                lastException = e
            } catch (e: java.io.IOException) {
                Timber.w("IO 错误: ${e.message}，重试 $attempt/$maxRetries...")
                lastException = e
            } catch (e: Exception) {
                // 其他错误不重试，直接返回
                return Result.failure(e)
            }

            if (attempt < maxRetries) {
                delay(retryDelayMs * attempt)
            }
        }

        return Result.failure(lastException ?: Exception("Unknown error"))
    }

    /**
     * 处理 HTTP 429 限流的延迟等待
     * @return 计算出的等待时间(ms)
     */
    protected fun calculateRateLimitDelay(
        retryAfterHeader: String?,
        attempt: Int,
        baseDelayMs: Long = RETRY_DELAY_MS
    ): Long {
        val retryAfter = retryAfterHeader?.toLongOrNull()
        return if (retryAfter != null) retryAfter * 1000 else baseDelayMs * attempt * 2
    }
}
