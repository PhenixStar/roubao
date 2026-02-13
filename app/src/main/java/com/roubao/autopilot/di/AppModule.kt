package com.roubao.autopilot.di

import com.roubao.autopilot.controller.AppScanner
import com.roubao.autopilot.data.SettingsManager
import org.koin.dsl.module

/**
 * Koin 依赖注入模块
 * 注册全局单例：SettingsManager, AppScanner
 */
val appModule = module {
    // 设置管理器（全局唯一，需要 Context）
    single { SettingsManager(get()) }

    // 应用扫描器（全局唯一，需要 Context）
    single { AppScanner(get()) }
}
