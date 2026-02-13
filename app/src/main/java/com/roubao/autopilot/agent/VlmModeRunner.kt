package com.roubao.autopilot.agent

import android.content.Context
import android.graphics.Bitmap
import com.roubao.autopilot.controller.AppScanner
import com.roubao.autopilot.controller.DeviceController
import com.roubao.autopilot.data.ExecutionStep
import com.roubao.autopilot.ui.OverlayService
import com.roubao.autopilot.vlm.GUIOwlClient
import com.roubao.autopilot.vlm.MAIUIAction
import com.roubao.autopilot.vlm.MAIUIClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.coroutineContext

/**
 * VLM 模式运行器 - GUI-Owl 和 MAI-UI 模式的主循环
 *
 * 从 MobileAgent 中提取，负责执行两种 VLM 专用模式的截图-预测-执行循环。
 * MobileAgent 作为入口点调用本类方法。
 */
class VlmModeRunner(
    private val controller: DeviceController,
    private val context: Context,
    private val appScanner: AppScanner,
    private val vlmActionExecutor: VlmActionExecutor,
    private val stateFlow: MutableStateFlow<AgentState>,
    private val log: (String) -> Unit,
    private val updateState: (AgentState.() -> AgentState) -> Unit,
    private val stopAgent: () -> Unit
) {

    /**
     * GUI-Owl 模式执行指令
     * 简化流程：截图 -> GUI-Owl API -> 解析操作 -> 执行
     */
    suspend fun runWithGUIOwl(
        client: GUIOwlClient,
        instruction: String,
        maxSteps: Int
    ): AgentResult {
        client.resetSession()
        log("GUI-Owl 会话已重置")

        val (screenWidth, screenHeight) = controller.getScreenSize()
        log("屏幕尺寸: ${screenWidth}x${screenHeight}")

        OverlayService.show(context, "GUI-Owl 模式") {
            updateState { copy(isRunning = false) }
            stopAgent()
        }
        updateState { copy(isRunning = true, currentStep = 0, instruction = instruction) }

        var consecutiveVlmFailures = 0

        try {
            for (step in 0 until maxSteps) {
                var screenshot: Bitmap? = null
                try {
                    coroutineContext.ensureActive()
                    if (!stateFlow.value.isRunning) return userStopped()

                    updateState { copy(currentStep = step + 1) }
                    log("\n========== Step ${step + 1} (GUI-Owl) ==========")
                    OverlayService.update("Step ${step + 1}/$maxSteps")

                    // 1. 截图
                    log("截图中...")
                    OverlayService.setVisible(false)
                    delay(100)
                    val screenshotResult = controller.screenshotWithFallback()
                    OverlayService.setVisible(true)
                    screenshot = screenshotResult.bitmap

                    if (screenshotResult.isSensitive) {
                        log("⚠️ 检测到敏感页面")
                        OverlayService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "敏感页面，已停止")
                    }

                    // 2. 调用 GUI-Owl API
                    log("调用 GUI-Owl API...")
                    val response = client.predict(instruction, screenshot)

                    if (response.isFailure) {
                        consecutiveVlmFailures++
                        log("GUI-Owl 调用失败: ${response.exceptionOrNull()?.message}")
                        val strategy = VlmErrorRecovery.getRecoveryStrategy(consecutiveVlmFailures)
                        if (strategy == null) {
                            log("GUI-Owl repeatedly failed ($consecutiveVlmFailures times), giving up")
                            OverlayService.update("VLM 多次失败，已停止")
                            delay(1500)
                            OverlayService.hide(context)
                            updateState { copy(isRunning = false) }
                            bringAppToFront()
                            return AgentResult(false, "VLM repeatedly failed")
                        }
                        if (strategy == VlmErrorRecovery.RecoveryStrategy.WAIT_AND_RETRY) {
                            log("VLM failure #$consecutiveVlmFailures, waiting 5s before retry")
                            delay(5000)
                        } else {
                            log("VLM failure #$consecutiveVlmFailures, retrying...")
                        }
                        continue
                    }

                    val result = response.getOrThrow()
                    log("思考: ${result.thought.take(100)}...")
                    log("操作: ${result.operation}")
                    log("说明: ${result.explanation}")

                    // 3. 解析操作指令
                    val parsedAction = client.parseOperation(result.operation)
                    if (parsedAction == null) {
                        consecutiveVlmFailures++
                        log("无法解析操作: ${result.operation}")
                        val strategy = VlmErrorRecovery.getRecoveryStrategy(consecutiveVlmFailures)
                        if (strategy == null) {
                            log("GUI-Owl parse repeatedly failed, giving up")
                            OverlayService.update("VLM 多次失败，已停止")
                            delay(1500)
                            OverlayService.hide(context)
                            updateState { copy(isRunning = false) }
                            bringAppToFront()
                            return AgentResult(false, "VLM repeatedly failed")
                        }
                        if (strategy == VlmErrorRecovery.RecoveryStrategy.WAIT_AND_RETRY) {
                            delay(5000)
                        }
                        continue
                    }
                    consecutiveVlmFailures = 0  // Reset on success

                    // 记录执行步骤
                    updateState {
                        copy(executionSteps = executionSteps + ExecutionStep(
                            stepNumber = step + 1,
                            timestamp = System.currentTimeMillis(),
                            action = parsedAction.type,
                            description = result.explanation,
                            thought = result.thought,
                            outcome = "?"
                        ))
                    }

                    // 检查是否完成
                    if (parsedAction.type == "finish") {
                        log("任务完成!")
                        OverlayService.update("完成!")
                        delay(1500)
                        OverlayService.hide(context)
                        updateState { copy(isRunning = false, isCompleted = true) }
                        bringAppToFront()
                        return AgentResult(success = true, message = "任务完成")
                    }

                    // 4. 执行动作
                    log("执行动作: ${parsedAction.type}")
                    OverlayService.update("${parsedAction.type}: ${result.explanation.take(15)}...")
                    vlmActionExecutor.executeGUIOwlAction(parsedAction, screenWidth, screenHeight)

                    // 更新步骤状态
                    updateState {
                        val updatedSteps = executionSteps.toMutableList()
                        if (step < updatedSteps.size) {
                            updatedSteps[step] = updatedSteps[step].copy(outcome = "A")
                        }
                        copy(executionSteps = updatedSteps)
                    }

                    delay(if (step == 0) 3000 else 1500)
                } finally {
                    screenshot?.let { if (!it.isRecycled) it.recycle() }
                }
            }
        } catch (e: CancellationException) {
            log("任务被取消")
            OverlayService.hide(context)
            updateState { copy(isRunning = false) }
            bringAppToFront()
            throw e
        }

        return maxStepsReached()
    }

    /**
     * MAI-UI 模式执行指令
     * 使用专用的 MAI-UI prompt 和对话历史管理
     */
    suspend fun runWithMAIUI(
        client: MAIUIClient,
        instruction: String,
        maxSteps: Int
    ): AgentResult {
        client.reset()
        log("MAI-UI 会话已重置")

        val (screenWidth, screenHeight) = controller.getScreenSize()
        log("屏幕尺寸: ${screenWidth}x${screenHeight}")

        val installedApps = appScanner.getApps().map { it.appName }
        client.setAvailableApps(installedApps)
        log("已加载 ${installedApps.size} 个应用")

        OverlayService.show(context, "MAI-UI 模式") {
            updateState { copy(isRunning = false) }
            stopAgent()
        }
        updateState { copy(isRunning = true, currentStep = 0, instruction = instruction) }

        var consecutiveVlmFailures = 0

        try {
            for (step in 0 until maxSteps) {
                var screenshot: Bitmap? = null
                try {
                    coroutineContext.ensureActive()
                    if (!stateFlow.value.isRunning) return userStopped()

                    updateState { copy(currentStep = step + 1) }
                    log("\n========== Step ${step + 1} (MAI-UI) ==========")
                    OverlayService.update("Step ${step + 1}/$maxSteps")

                    // 1. 截图
                    log("截图中...")
                    OverlayService.setVisible(false)
                    delay(100)
                    val screenshotResult = controller.screenshotWithFallback()
                    OverlayService.setVisible(true)
                    screenshot = screenshotResult.bitmap

                    if (screenshotResult.isSensitive) {
                        log("⚠️ 检测到敏感页面")
                        OverlayService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "敏感页面，已停止")
                    }

                    // 2. 调用 MAI-UI API
                    log("调用 MAI-UI API...")
                    val response = client.predict(instruction, screenshot)

                    if (response.isFailure) {
                        consecutiveVlmFailures++
                        log("MAI-UI 调用失败: ${response.exceptionOrNull()?.message}")
                        val strategy = VlmErrorRecovery.getRecoveryStrategy(consecutiveVlmFailures)
                        if (strategy == null) {
                            log("MAI-UI repeatedly failed ($consecutiveVlmFailures times), giving up")
                            OverlayService.update("VLM 多次失败，已停止")
                            delay(1500)
                            OverlayService.hide(context)
                            updateState { copy(isRunning = false) }
                            bringAppToFront()
                            return AgentResult(false, "VLM repeatedly failed")
                        }
                        if (strategy == VlmErrorRecovery.RecoveryStrategy.WAIT_AND_RETRY) {
                            log("VLM failure #$consecutiveVlmFailures, waiting 5s before retry")
                            delay(5000)
                        } else {
                            log("VLM failure #$consecutiveVlmFailures, retrying...")
                        }
                        continue
                    }

                    val result = response.getOrThrow()
                    log("思考: ${result.thinking.take(150)}...")

                    val action = result.action
                    if (action == null) {
                        consecutiveVlmFailures++
                        log("无法解析动作")
                        val strategy = VlmErrorRecovery.getRecoveryStrategy(consecutiveVlmFailures)
                        if (strategy == null) {
                            log("MAI-UI parse repeatedly failed, giving up")
                            OverlayService.update("VLM 多次失败，已停止")
                            delay(1500)
                            OverlayService.hide(context)
                            updateState { copy(isRunning = false) }
                            bringAppToFront()
                            return AgentResult(false, "VLM repeatedly failed")
                        }
                        if (strategy == VlmErrorRecovery.RecoveryStrategy.WAIT_AND_RETRY) {
                            delay(5000)
                        }
                        continue
                    }
                    consecutiveVlmFailures = 0  // Reset on success

                    log("动作: ${action.type}")

                    // 记录执行步骤
                    updateState {
                        copy(executionSteps = executionSteps + ExecutionStep(
                            stepNumber = step + 1,
                            timestamp = System.currentTimeMillis(),
                            action = action.type,
                            description = result.thinking.take(50),
                            thought = result.thinking,
                            outcome = "?"
                        ))
                    }

                    // 检查终止/特殊动作
                    val specialResult = handleSpecialAction(action)
                    if (specialResult != null) return specialResult

                    // 3. 执行动作
                    log("执行动作: ${action.type}")
                    OverlayService.update("${action.type}...")
                    vlmActionExecutor.executeMAIUIAction(action, screenWidth, screenHeight)

                    // 更新步骤状态
                    updateState {
                        val updatedSteps = executionSteps.toMutableList()
                        if (step < updatedSteps.size) {
                            updatedSteps[step] = updatedSteps[step].copy(outcome = "A")
                        }
                        copy(executionSteps = updatedSteps)
                    }

                    delay(if (step == 0) 2000 else 1000)
                } finally {
                    screenshot?.let { if (!it.isRecycled) it.recycle() }
                }
            }
        } catch (e: CancellationException) {
            log("任务被取消")
            OverlayService.hide(context)
            updateState { copy(isRunning = false) }
            bringAppToFront()
            throw e
        }

        return maxStepsReached()
    }

    // ==================== Private helpers ====================

    /** 处理 MAI-UI 特殊动作 (terminate, ask_user, answer)，返回 null 表示非特殊动作 */
    private suspend fun handleSpecialAction(action: MAIUIAction): AgentResult? {
        when (action.type) {
            "terminate" -> {
                val success = action.status == "success"
                log(if (success) "任务完成!" else "任务失败")
                OverlayService.update(if (success) "完成!" else "失败")
                delay(1500)
                OverlayService.hide(context)
                updateState { copy(isRunning = false, isCompleted = success) }
                bringAppToFront()
                return AgentResult(success = success, message = if (success) "任务完成" else "任务失败")
            }
            "ask_user" -> {
                log("请求用户介入: ${action.text}")
                OverlayService.update("请手动操作: ${action.text?.take(20)}")
                delay(5000)
                return null  // continue loop
            }
            "answer" -> {
                log("回答: ${action.text}")
                OverlayService.update("答案: ${action.text?.take(30)}")
                delay(3000)
                OverlayService.hide(context)
                updateState { copy(isRunning = false, isCompleted = true) }
                bringAppToFront()
                return AgentResult(success = true, message = "回答: ${action.text}")
            }
        }
        return null
    }

    /** 用户停止的标准返回 */
    private fun userStopped(): AgentResult {
        log("用户停止执行")
        OverlayService.hide(context)
        bringAppToFront()
        return AgentResult(success = false, message = "用户停止")
    }

    /** 达到最大步数的标准返回 */
    private suspend fun maxStepsReached(): AgentResult {
        log("达到最大步数限制")
        OverlayService.update("达到最大步数")
        delay(1500)
        OverlayService.hide(context)
        updateState { copy(isRunning = false, isCompleted = false) }
        bringAppToFront()
        return AgentResult(success = false, message = "达到最大步数限制")
    }

    /** 返回肉包App */
    private fun bringAppToFront() {
        AgentLoopHelper.bringAppToFront(context, log)
    }
}
