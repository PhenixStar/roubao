package com.roubao.autopilot.controller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.roubao.autopilot.App
import com.roubao.autopilot.IShellService
import com.roubao.autopilot.service.ShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 设备控制器 - 通过 Shizuku UserService 执行 shell 命令
 */
class DeviceController(private val context: Context? = null) {

    companion object {
        // 使用 /data/local/tmp，shell 用户有权限访问
        private const val SCREENSHOT_PATH = "/data/local/tmp/autopilot_screen.png"
    }

    @Volatile
    private var shellService: IShellService? = null
    @Volatile
    private var serviceBound = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clipboardManager: ClipboardManager? by lazy {
        context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "com.roubao.autopilot",
            ShellService::class.java.name
        )
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(true)
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = IShellService.Stub.asInterface(service)
            serviceBound = true
            Timber.d("ShellService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            serviceBound = false
            Timber.d("ShellService disconnected")
        }
    }

    /**
     * 绑定 Shizuku UserService
     */
    fun bindService() {
        if (!isShizukuAvailable()) {
            Timber.w("Shizuku not available")
            return
        }
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解绑服务
     */
    fun unbindService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 检查 Shizuku 是否可用
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查服务是否可用
     */
    fun isAvailable(): Boolean {
        return serviceBound && shellService != null
    }

    /**
     * Shizuku 权限级别
     */
    enum class ShizukuPrivilegeLevel {
        NONE,       // 未连接
        ADB,        // ADB 模式 (UID 2000)
        ROOT        // Root 模式 (UID 0)
    }

    /**
     * 获取当前 Shizuku 权限级别
     * UID 0 = root, UID 2000 = shell (ADB)
     */
    fun getShizukuPrivilegeLevel(): ShizukuPrivilegeLevel {
        if (!isAvailable()) {
            return ShizukuPrivilegeLevel.NONE
        }
        return try {
            val uid = Shizuku.getUid()
            Timber.d("Shizuku UID: $uid")
            when (uid) {
                0 -> ShizukuPrivilegeLevel.ROOT
                else -> ShizukuPrivilegeLevel.ADB
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ShizukuPrivilegeLevel.NONE
        }
    }

    /**
     * 执行 shell 命令 (本地，无权限)
     */
    private fun execLocal(command: String): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            // Drain error stream in background to prevent buffer fill hang
            val errorDrainer = Thread {
                try {
                    process.errorStream.bufferedReader().readText()
                } catch (_: Exception) { }
            }.apply { isDaemon = true; start() }

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) {
                Timber.w("execLocal timed out after 10s, destroying process")
                process.destroyForcibly()
            }
            errorDrainer.join(1000)
            output
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            process?.destroy()
        }
    }

    /**
     * 执行 shell 命令 (通过 Shizuku)
     */
    private fun exec(command: String): String {
        return try {
            val service = shellService
            if (service != null) {
                service.exec(command)
            } else {
                Timber.w("Shizuku unavailable, falling back to local shell (reduced privileges)")
                execLocal(command)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.w("Shizuku unavailable, falling back to local shell (reduced privileges)")
            execLocal(command)
        }
    }

    /**
     * 点击屏幕
     */
    fun tap(x: Int, y: Int) {
        exec("input tap $x $y")
    }

    /**
     * 长按
     */
    fun longPress(x: Int, y: Int, durationMs: Int = 1000) {
        exec("input swipe $x $y $x $y $durationMs")
    }

    /**
     * 双击
     */
    fun doubleTap(x: Int, y: Int) {
        exec("input tap $x $y && input tap $x $y")
    }

    /**
     * 滑动
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 500) {
        exec("input swipe $x1 $y1 $x2 $y2 $durationMs")
    }

    /**
     * 输入文本 (使用剪贴板方式，支持中文)
     */
    fun type(text: String) {
        // 检查是否包含非 ASCII 字符
        val hasNonAscii = text.any { it.code > 127 }

        if (hasNonAscii) {
            // 中文等使用剪贴板方式
            typeViaClipboard(text)
        } else {
            // 纯英文数字使用 input text
            val escaped = text.replace("'", "'\\''")
            exec("input text '$escaped'")
        }
    }

    /**
     * 通过剪贴板方式输入中文
     * 使用 Android ClipboardManager API 设置剪贴板，然后发送粘贴按键
     */
    private fun typeViaClipboard(text: String) {
        Timber.d("尝试输入中文: $text")

        // 方法1: 使用 Android 剪贴板 API + 粘贴 (最可靠，不需要额外 App)
        if (clipboardManager != null) {
            try {
                // 使用 CountDownLatch 等待剪贴板设置完成
                val latch = CountDownLatch(1)
                var clipboardSet = false

                // 必须在主线程操作剪贴板
                mainHandler.post {
                    try {
                        val clip = ClipData.newPlainText("baozi_input", text)
                        clipboardManager?.setPrimaryClip(clip)
                        clipboardSet = true
                        Timber.d("已设置剪贴板: $text")
                    } catch (e: Exception) {
                        Timber.e("设置剪贴板异常: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }

                // 等待剪贴板设置完成 (最多等 1 秒)
                val success = latch.await(1, TimeUnit.SECONDS)
                if (!success) {
                    Timber.e("等待剪贴板超时")
                    return
                }

                if (!clipboardSet) {
                    Timber.e("剪贴板设置失败")
                    return
                }

                // 稍等一下确保剪贴板生效
                Thread.sleep(200)

                // 发送粘贴按键 (KEYCODE_PASTE = 279)
                exec("input keyevent 279")
                Timber.d("已发送粘贴按键")
                return
            } catch (e: Exception) {
                Timber.e("剪贴板方式失败: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Timber.e("ClipboardManager 为 null，Context 未设置")
        }

        // 方法2: 使用 ADB Keyboard 广播 (备选，需要安装 ADBKeyboard)
        // Escape shell metacharacters to prevent injection in double-quoted string
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\$")
            .replace("`", "\\`")
        val adbKeyboardResult = exec("am broadcast -a ADB_INPUT_TEXT --es msg \"$escaped\"")
        Timber.d("ADBKeyboard 广播结果: $adbKeyboardResult")

        if (adbKeyboardResult.contains("result=0")) {
            Timber.d("ADBKeyboard 输入成功")
            return
        }

        // 方法3: 使用 cmd input text (Android 12+ 可能支持 UTF-8)
        // Use single-quote escaping to prevent shell injection
        Timber.d("尝试 cmd input text...")
        val singleQuoteEscaped = text.replace("'", "'\\''")
        exec("cmd input text '$singleQuoteEscaped'")
    }

    /**
     * 输入文本 (逐字符，兼容性更好)
     */
    fun typeCharByChar(text: String) {
        text.forEach { char ->
            when {
                char == ' ' -> exec("input text %s")
                char == '\n' -> exec("input keyevent 66")
                char.isLetterOrDigit() && char.code <= 127 -> exec("input text $char")
                char in "-.,!?@'/:;()" -> {
                    // Escape shell metacharacters in double-quoted context
                    val safeChar = when (char) {
                        '$' -> "\\$"
                        '`' -> "\\`"
                        '\\' -> "\\\\"
                        '"' -> "\\\""
                        else -> "$char"
                    }
                    exec("input text \"$safeChar\"")
                }
                else -> {
                    // 非 ASCII 字符使用广播
                    // Escape shell metacharacters for double-quoted string
                    val safeChar = "$char"
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\$", "\\$")
                        .replace("`", "\\`")
                    exec("am broadcast -a ADB_INPUT_TEXT --es msg \"$safeChar\"")
                }
            }
        }
    }

    /**
     * 返回键
     */
    fun back() {
        exec("input keyevent 4")
    }

    /**
     * Home 键
     */
    fun home() {
        exec("input keyevent 3")
    }

    /**
     * 回车键
     */
    fun enter() {
        exec("input keyevent 66")
    }

    private var cacheDir: File? = null

    fun setCacheDir(dir: File) {
        cacheDir = dir
    }

    /**
     * 截图结果
     */
    data class ScreenshotResult(
        val bitmap: Bitmap,
        val isSensitive: Boolean = false,  // 是否是敏感页面（截图失败）
        val isFallback: Boolean = false    // 是否是降级的黑屏占位图
    )

    /**
     * 截图 - 使用 /data/local/tmp 并设置全局可读权限
     * 失败时返回黑屏占位图（降级处理）
     */
    suspend fun screenshotWithFallback(): ScreenshotResult = withContext(Dispatchers.IO) {
        try {
            // 截图到 /data/local/tmp 并设置权限让 App 可读
            val output = exec("screencap -p $SCREENSHOT_PATH && chmod 666 $SCREENSHOT_PATH")
            delay(500)

            // 检查是否截图失败（敏感页面保护）
            if (output.contains("Status: -1") || output.contains("Failed") || output.contains("error")) {
                Timber.w("Screenshot blocked (sensitive screen), returning fallback")
                return@withContext createFallbackScreenshot(isSensitive = true)
            }

            // 尝试直接读取
            val file = File(SCREENSHOT_PATH)
            if (file.exists() && file.canRead() && file.length() > 0) {
                Timber.d("Reading screenshot from: $SCREENSHOT_PATH, size: ${file.length()}")
                val bitmap = BitmapFactory.decodeFile(SCREENSHOT_PATH)
                if (bitmap != null) {
                    return@withContext ScreenshotResult(bitmap)
                }
            }

            // 如果无法直接读取，通过 shell cat 读取二进制数据
            // Only attempt su -c if root mode is available
            if (getShizukuPrivilegeLevel() == ShizukuPrivilegeLevel.ROOT) {
                Timber.d("Cannot read directly, trying su -c cat (root available)...")
                var suProcess: Process? = null
                try {
                    suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $SCREENSHOT_PATH"))
                    val bytes = suProcess.inputStream.readBytes()
                    val completed = suProcess.waitFor(5, TimeUnit.SECONDS)
                    if (!completed) {
                        Timber.w("su -c cat timed out, destroying process")
                        suProcess.destroyForcibly()
                    } else if (bytes.isNotEmpty()) {
                        Timber.d("Read ${bytes.size} bytes via shell")
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            return@withContext ScreenshotResult(bitmap)
                        }
                    }
                } finally {
                    suProcess?.destroy()
                }
            } else {
                Timber.w("Cannot read directly, root not available for su -c fallback")
            }

            Timber.w("Screenshot file empty or not accessible, returning fallback")
            createFallbackScreenshot(isSensitive = false)
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e("Screenshot exception, returning fallback")
            createFallbackScreenshot(isSensitive = false)
        }
    }

    /**
     * 创建黑屏占位图（降级处理）
     */
    private fun createFallbackScreenshot(isSensitive: Boolean): ScreenshotResult {
        val (width, height) = getScreenSize()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 默认是黑色，无需填充
        return ScreenshotResult(
            bitmap = bitmap,
            isSensitive = isSensitive,
            isFallback = true
        )
    }

    /**
     * 截图 - 使用 /data/local/tmp 并设置全局可读权限
     * @deprecated 使用 screenshotWithFallback() 代替
     */
    suspend fun screenshot(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 截图到 /data/local/tmp 并设置权限让 App 可读
            exec("screencap -p $SCREENSHOT_PATH && chmod 666 $SCREENSHOT_PATH")
            delay(500)

            // 尝试直接读取
            val file = File(SCREENSHOT_PATH)
            if (file.exists() && file.canRead() && file.length() > 0) {
                Timber.d("Reading screenshot from: $SCREENSHOT_PATH, size: ${file.length()}")
                return@withContext BitmapFactory.decodeFile(SCREENSHOT_PATH)
            }

            // 如果无法直接读取，通过 shell cat 读取二进制数据
            // Only attempt su -c if root mode is available
            if (getShizukuPrivilegeLevel() == ShizukuPrivilegeLevel.ROOT) {
                Timber.d("Cannot read directly, trying su -c cat (root available)...")
                var suProcess: Process? = null
                try {
                    suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $SCREENSHOT_PATH"))
                    val bytes = suProcess.inputStream.readBytes()
                    val completed = suProcess.waitFor(5, TimeUnit.SECONDS)
                    if (!completed) {
                        Timber.w("su -c cat timed out, destroying process")
                        suProcess.destroyForcibly()
                    } else if (bytes.isNotEmpty()) {
                        Timber.d("Read ${bytes.size} bytes via shell")
                        return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } finally {
                    suProcess?.destroy()
                }
            } else {
                Timber.w("Cannot read directly, root not available for su -c fallback")
            }

            Timber.w("Screenshot file empty or not accessible")
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取屏幕尺寸（考虑屏幕方向）
     */
    fun getScreenSize(): Pair<Int, Int> {
        val output = exec("wm size")
        // 输出格式: Physical size: 1080x2400
        val match = Regex("(\\d+)x(\\d+)").find(output)
        val (physicalWidth, physicalHeight) = if (match != null) {
            val (w, h) = match.destructured
            Pair(w.toInt(), h.toInt())
        } else {
            // Try DisplayMetrics as secondary source before hardcoded fallback
            val ctx = context
            if (ctx != null) {
                val dm = ctx.resources.displayMetrics
                Timber.w("wm size parse failed, using DisplayMetrics (${dm.widthPixels}x${dm.heightPixels})")
                Pair(dm.widthPixels, dm.heightPixels)
            } else {
                Timber.w("wm size parse failed, using hardcoded 1080x2400 fallback")
                Pair(1080, 2400)
            }
        }

        // 检测屏幕方向
        val orientation = getScreenOrientation()
        return if (orientation == 1 || orientation == 3) {
            // 横屏：交换宽高
            Pair(physicalHeight, physicalWidth)
        } else {
            // 竖屏
            Pair(physicalWidth, physicalHeight)
        }
    }

    /**
     * 获取屏幕方向
     * @return 0=竖屏, 1=横屏(90°), 2=倒置竖屏, 3=横屏(270°)
     */
    private fun getScreenOrientation(): Int {
        val output = exec("dumpsys window displays | grep mCurrentOrientation")
        // 输出格式: mCurrentOrientation=0 或 mCurrentOrientation=1
        val match = Regex("mCurrentOrientation=(\\d)").find(output)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * 打开 App - 支持包名或应用名
     */
    fun openApp(appNameOrPackage: String) {
        // 常见应用名到包名的映射 (作为备选)
        val packageMap = mapOf(
            "settings" to "com.android.settings",
            "设置" to "com.android.settings",
            "chrome" to "com.android.chrome",
            "浏览器" to "com.android.browser",
            "camera" to "com.android.camera",
            "相机" to "com.android.camera",
            "phone" to "com.android.dialer",
            "电话" to "com.android.dialer",
            "contacts" to "com.android.contacts",
            "联系人" to "com.android.contacts",
            "messages" to "com.android.mms",
            "短信" to "com.android.mms",
            "gallery" to "com.android.gallery3d",
            "相册" to "com.android.gallery3d",
            "clock" to "com.android.deskclock",
            "时钟" to "com.android.deskclock",
            "calculator" to "com.android.calculator2",
            "计算器" to "com.android.calculator2",
            "calendar" to "com.android.calendar",
            "日历" to "com.android.calendar",
            "files" to "com.android.documentsui",
            "文件" to "com.android.documentsui"
        )

        val lowerName = appNameOrPackage.lowercase().trim()
        val finalPackage: String

        if (appNameOrPackage.contains(".")) {
            // 已经是包名格式
            finalPackage = appNameOrPackage
        } else if (packageMap.containsKey(lowerName)) {
            // 从内置映射中查找
            finalPackage = packageMap[lowerName]!!
        } else {
            // 使用 AppScanner 搜索应用
            val appScanner = App.getInstance().appScanner
            val searchResults = appScanner.searchApps(appNameOrPackage, topK = 1)
            if (searchResults.isNotEmpty()) {
                finalPackage = searchResults[0].app.packageName
                Timber.d("AppScanner found: ${searchResults[0].app.appName} -> $finalPackage")
            } else {
                // 找不到，直接用原始输入尝试
                finalPackage = appNameOrPackage
                Timber.w("App not found in AppScanner: $appNameOrPackage")
            }
        }

        // Validate package name to prevent command injection
        val packageNamePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        if (!packageNamePattern.matches(finalPackage)) {
            Timber.e("Invalid package name rejected: $finalPackage")
            return
        }

        // 使用 monkey 命令启动应用 (最可靠)
        val result = exec("monkey -p $finalPackage -c android.intent.category.LAUNCHER 1 2>/dev/null")
        Timber.d("openApp: $appNameOrPackage -> $finalPackage, result: $result")
    }

    /**
     * Escape shell metacharacters for safe use inside double-quoted strings.
     * Prevents $(), ``, and \ injection.
     */
    private fun escapeShellDoubleQuoted(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\$")
            .replace("`", "\\`")
            .replace("!", "\\!")
    }

    /**
     * 通过 Intent 打开
     */
    fun openIntent(action: String, data: String? = null) {
        val cmd = buildString {
            append("am start -a $action")
            if (data != null) {
                val safeData = escapeShellDoubleQuoted(data)
                append(" -d \"$safeData\"")
            }
        }
        exec(cmd)
    }

    /**
     * 打开 DeepLink
     */
    fun openDeepLink(uri: String) {
        val safeUri = escapeShellDoubleQuoted(uri)
        exec("am start -a android.intent.action.VIEW -d \"$safeUri\"")
    }
}
