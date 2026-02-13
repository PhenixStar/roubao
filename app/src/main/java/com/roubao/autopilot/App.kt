package com.roubao.autopilot

import android.app.Application
import android.content.pm.PackageManager
import com.roubao.autopilot.controller.AppScanner
import com.roubao.autopilot.controller.DeviceController
import com.roubao.autopilot.data.SettingsManager
import com.roubao.autopilot.skills.SkillManager
import com.roubao.autopilot.tools.ToolManager
import com.roubao.autopilot.utils.CrashHandler
import rikka.shizuku.Shizuku
import timber.log.Timber

class App : Application() {

    lateinit var deviceController: DeviceController
        private set
    lateinit var appScanner: AppScanner
        private set
    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化 Timber 日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 初始化崩溃捕获（本地日志）
        CrashHandler.getInstance().init(this)

        // 初始化设置管理器（全局唯一实例）
        settingsManager = SettingsManager(this)

        // 初始化 Sentry（根据用户设置决定是否启用）
        // 用户需在 AndroidManifest.xml 或此处设置自己的 DSN 以启用云端上报
        val cloudCrashReportEnabled = settingsManager.settings.value.cloudCrashReportEnabled
        io.sentry.android.core.SentryAndroid.init(this) { options ->
            options.dsn = "" // 留空 = 禁用，用户需配置自己的 Sentry DSN
            options.isEnableAutoSessionTracking = true
            options.tracesSampleRate = 0.2
            options.isEnabled = cloudCrashReportEnabled
        }
        Timber.d("云端崩溃上报: ${if (cloudCrashReportEnabled) "已开启" else "已关闭"}")

        // 初始化 Shizuku
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)

        // 初始化核心组件
        initializeComponents()
    }

    private fun initializeComponents() {
        // 初始化设备控制器
        deviceController = DeviceController(this)
        deviceController.setCacheDir(cacheDir)

        // 初始化应用扫描器
        appScanner = AppScanner(this)

        // 初始化 Tools 层
        val toolManager = ToolManager.init(this, deviceController, appScanner, settingsManager)

        // 异步预扫描应用列表（避免 ANR）
        Timber.d("开始异步扫描已安装应用...")
        Thread {
            appScanner.refreshApps()
            Timber.d("已扫描 %d 个应用", appScanner.getApps().size)
        }.start()

        // 初始化 Skills 层（传入 appScanner 用于检测已安装应用）
        val skillManager = SkillManager.init(this, toolManager, appScanner)
        Timber.d("SkillManager 已加载 %d 个 Skills", skillManager.getAllSkills().size)

        Timber.d("组件初始化完成")
    }

    override fun onTerminate() {
        super.onTerminate()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    /**
     * 动态更新云端崩溃上报开关
     */
    fun updateCloudCrashReportEnabled(enabled: Boolean) {
        // Sentry does not support runtime toggle; log the preference change
        Timber.d("云端崩溃上报已${if (enabled) "开启" else "关闭"}")
    }

    companion object {
        @Volatile
        private var instance: App? = null

        fun getInstance(): App {
            return instance ?: throw IllegalStateException("App 未初始化")
        }

        private val REQUEST_PERMISSION_RESULT_LISTENER =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Timber.d("Shizuku permission result: %s", granted)
            }
    }
}
