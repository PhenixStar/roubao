package com.roubao.autopilot.agent

import android.content.Context
import android.graphics.Bitmap
import com.roubao.autopilot.controller.AppScanner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.roubao.autopilot.controller.DeviceController
import com.roubao.autopilot.data.ExecutionStep
import com.roubao.autopilot.skills.SkillManager
import com.roubao.autopilot.ui.OverlayService
import com.roubao.autopilot.vlm.GUIOwlClient
import com.roubao.autopilot.vlm.MAIUIClient
import com.roubao.autopilot.vlm.VLMClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.coroutineContext

/**
 * Mobile Agent 主循环 - 移植自 MobileAgent-v3
 *
 * 新增 Skill 层支持：
 * - 快速路径：高置信度 delegation Skill 直接执行
 * - 增强模式：GUI 自动化 Skill 提供上下文指导
 *
 * 支持三种模式：
 * - OpenAI 兼容模式：使用 VLMClient (Manager -> Executor -> Reflector)
 * - GUI-Owl 模式：使用 GUIOwlClient (直接返回操作指令)
 * - MAI-UI 模式：使用 MAIUIClient (专用 prompt 和对话历史)
 */
class MobileAgent(
    private val vlmClient: VLMClient?,
    private val controller: DeviceController,
    private val context: Context,
    private val guiOwlClient: GUIOwlClient? = null,  // GUI-Owl 专用客户端
    private val maiuiClient: MAIUIClient? = null     // MAI-UI 专用客户端
) : KoinComponent {
    private val useGUIOwlMode: Boolean = guiOwlClient != null
    private val useMAIUIMode: Boolean = maiuiClient != null
    private val appScanner: AppScanner by inject()
    private val manager = Manager()
    private val executor = Executor()
    private val reflector = ActionReflector()
    private val notetaker = Notetaker()

    // 状态流 (must be initialized before vlmModeRunner)
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    // 停止回调（由 MainActivity 设置，用于取消协程）
    var onStopRequested: (() -> Unit)? = null

    // 动作执行器
    private val actionExecutor = ActionExecutor(controller, appScanner, ::log)
    private val vlmActionExecutor = VlmActionExecutor(controller, ::log)

    // VLM 模式运行器 (GUI-Owl / MAI-UI)
    private val vlmModeRunner = VlmModeRunner(
        controller, context, appScanner, vlmActionExecutor,
        _state, ::log, ::updateState, ::stop
    )

    // Skill 管理器
    private val skillManager: SkillManager? = try {
        SkillManager.getInstance().also {
            Timber.d("SkillManager 已加载，共 ${it.getAllSkills().size} 个 Skills")
            vlmClient?.let { client -> it.setVLMClient(client) }
        }
    } catch (e: Exception) {
        Timber.e("SkillManager 加载失败: ${e.message}")
        null
    }

    /** 执行指令 - 根据模式路由到对应执行器 */
    suspend fun runInstruction(
        instruction: String,
        maxSteps: Int = 25,
        useNotetaker: Boolean = false
    ): AgentResult {
        log("开始执行: $instruction")

        if (useGUIOwlMode && guiOwlClient != null) {
            log("使用 GUI-Owl 模式")
            return vlmModeRunner.runWithGUIOwl(guiOwlClient, instruction, maxSteps)
        }
        if (useMAIUIMode && maiuiClient != null) {
            log("使用 MAI-UI 模式")
            return vlmModeRunner.runWithMAIUI(maiuiClient, instruction, maxSteps)
        }
        if (vlmClient == null) {
            log("错误: VLMClient 未初始化")
            return AgentResult(success = false, message = "VLMClient 未初始化")
        }

        log("使用 OpenAI 兼容模式")
        return runInstructionOpenAI(instruction, maxSteps, useNotetaker)
    }

    /** 停止执行 */
    fun stop() {
        OverlayService.hide(context)
        updateState { copy(isRunning = false) }
        onStopRequested?.invoke()
    }

    /** 清空日志 */
    fun clearLogs() {
        _logs.value = emptyList()
        updateState { copy(executionSteps = emptyList()) }
    }

    // ==================== OpenAI 兼容模式主循环 ====================

    private suspend fun runInstructionOpenAI(
        instruction: String,
        maxSteps: Int,
        useNotetaker: Boolean
    ): AgentResult {
        val vlm = vlmClient!!
        val infoPool = prepareInfoPool(instruction)

        OverlayService.show(context, "开始执行...") {
            updateState { copy(isRunning = false) }
            stop()
        }
        updateState { copy(isRunning = true, currentStep = 0, instruction = instruction) }

        var consecutiveVlmFailures = 0

        try {
            for (step in 0 until maxSteps) {
                var screenshot: Bitmap? = null
                var afterScreenshot: Bitmap? = null
                try {
                    coroutineContext.ensureActive()
                    if (!_state.value.isRunning) return userStopped()

                    updateState { copy(currentStep = step + 1) }
                    log("\n========== Step ${step + 1} ==========")
                    OverlayService.update("Step ${step + 1}/$maxSteps")

                    // 1. 截图
                    screenshot = takeScreenshot() ?: continue
                    if (!_state.value.isRunning) return userStopped()

                    // 2. 检查错误升级 + Manager 规划
                    AgentLoopHelper.checkErrorEscalation(infoPool)
                    val managerResult = runManagerPhase(vlm, infoPool, screenshot)
                    if (managerResult != null) return managerResult

                    // 3. Executor 决定动作
                    val output = runExecutorPhase(vlm, infoPool, screenshot)
                    if (output == null) {
                        // VLM failure or parse failure - apply recovery
                        consecutiveVlmFailures++
                        val strategy = VlmErrorRecovery.getRecoveryStrategy(consecutiveVlmFailures)
                        if (strategy == null) {
                            log("VLM repeatedly failed ($consecutiveVlmFailures times), giving up")
                            OverlayService.update("VLM 多次失败，已停止")
                            delay(1500)
                            OverlayService.hide(context)
                            updateState { copy(isRunning = false) }
                            bringAppToFront()
                            return AgentResult(false, "VLM repeatedly failed")
                        }
                        when (strategy) {
                            VlmErrorRecovery.RecoveryStrategy.RETRY_NORMAL -> {
                                log("VLM failure #$consecutiveVlmFailures, retrying...")
                            }
                            VlmErrorRecovery.RecoveryStrategy.RETRY_SIMPLIFIED -> {
                                log("VLM failure #$consecutiveVlmFailures, retrying with simplified approach")
                            }
                            VlmErrorRecovery.RecoveryStrategy.WAIT_AND_RETRY -> {
                                log("VLM failure #$consecutiveVlmFailures, waiting 5s before retry")
                                delay(5000)
                            }
                        }
                        continue
                    }
                    consecutiveVlmFailures = 0  // Reset on success
                    val (executorResult, action) = output

                    // 4. 敏感操作确认
                    if (!confirmSensitiveAction(action, executorResult, infoPool)) continue

                    // 5. 执行动作
                    log("执行动作: ${action.type}")
                    OverlayService.update("${action.type}: ${executorResult.description.take(15)}...")
                    actionExecutor.executeAction(action, infoPool)
                    infoPool.lastAction = action

                    val stepIdx = _state.value.executionSteps.size
                    updateState { copy(executionSteps = executionSteps + ExecutionStep(
                        stepNumber = step + 1, timestamp = System.currentTimeMillis(),
                        action = action.type, description = executorResult.description,
                        thought = executorResult.thought, outcome = "?"
                    )) }

                    delay(if (step == 0) 5000 else 2000)
                    if (!_state.value.isRunning) return userStopped()

                    // 6. Reflector 反思
                    afterScreenshot = takeScreenshotForReflection()
                    val reflectResult = runReflectorPhase(vlm, infoPool, screenshot, afterScreenshot)
                    infoPool.actionHistory.add(action)
                    infoPool.summaryHistory.add(executorResult.description)
                    infoPool.actionOutcomes.add(reflectResult.outcome)
                    infoPool.errorDescriptions.add(reflectResult.errorDescription)
                    infoPool.progressStatus = infoPool.completedPlan

                    updateState {
                        val updated = executionSteps.toMutableList()
                        if (stepIdx < updated.size) updated[stepIdx] = updated[stepIdx].copy(outcome = reflectResult.outcome)
                        copy(executionSteps = updated)
                    }

                    // 7. Notetaker (可选)
                    if (useNotetaker && reflectResult.outcome == "A" && action.type != "answer") {
                        runNotetakerPhase(vlm, infoPool, afterScreenshot)
                    }
                } finally {
                    screenshot?.let { if (!it.isRecycled) it.recycle() }
                    afterScreenshot?.let { if (!it.isRecycled) it.recycle() }
                }
            }
        } catch (e: CancellationException) {
            log("任务被取消"); OverlayService.hide(context)
            updateState { copy(isRunning = false) }; bringAppToFront(); throw e
        }

        return maxStepsReached()
    }

    // ==================== Phase helpers ====================

    private suspend fun prepareInfoPool(instruction: String): InfoPool {
        log("正在分析意图...")
        val skillContext = skillManager?.generateAgentContextWithLLM(instruction)
        val infoPool = InfoPool(instruction = instruction)

        val prompt = "You are an agent who can operate an Android phone. " +
                "Decide the next action based on the current state.\n\nUser Request: $instruction\n"
        infoPool.executorMemory = ConversationMemory.withSystemPrompt(prompt)
        log("已初始化对话记忆")

        if (!skillContext.isNullOrEmpty() && skillContext != "未找到相关技能或可用应用，请使用通用 GUI 自动化完成任务。") {
            infoPool.skillContext = skillContext
            log("已匹配到可用技能:\n$skillContext")
        } else { log("未匹配到特定技能，使用通用 GUI 自动化") }

        val (w, h) = controller.getScreenSize()
        infoPool.screenWidth = w; infoPool.screenHeight = h
        log("屏幕尺寸: ${w}x${h}")

        val apps = appScanner.getApps().filter { !it.isSystem }.take(50).map { it.appName }
        infoPool.installedApps = apps.joinToString(", ")
        log("已加载 ${apps.size} 个应用")

        return infoPool
    }

    /** Manager 规划阶段。返回 AgentResult 表示任务结束，null 表示继续 */
    private suspend fun runManagerPhase(vlm: VLMClient, infoPool: InfoPool, screenshot: Bitmap): AgentResult? {
        val skip = !infoPool.errorFlagPlan && infoPool.actionHistory.isNotEmpty() &&
                infoPool.actionHistory.last().type == "invalid"
        if (skip) return null

        log("Manager 规划中...")
        if (!_state.value.isRunning) return userStopped()
        val resp = vlm.predict(manager.getPrompt(infoPool), listOf(screenshot))
        if (!_state.value.isRunning) return userStopped()
        if (resp.isFailure) { log("Manager 调用失败: ${resp.exceptionOrNull()?.message}"); return null }

        val plan = manager.parseResponse(resp.getOrThrow())
        infoPool.completedPlan = plan.completedSubgoal; infoPool.plan = plan.plan
        log("计划: ${plan.plan.take(100)}...")

        if (plan.plan.contains("STOP_SENSITIVE")) {
            log("检测到敏感页面（支付/密码等），已停止执行")
            OverlayService.update("敏感页面，已停止"); delay(2000); OverlayService.hide(context)
            updateState { copy(isRunning = false, isCompleted = false) }; bringAppToFront()
            return AgentResult(success = false, message = "检测到敏感页面（支付/密码），已安全停止")
        }

        if (plan.plan.trim().let {
            it.equals("Finished", ignoreCase = true) || it.equals("Finished.", ignoreCase = true) ||
            it.matches(Regex("^\\s*(task\\s+)?finished\\.?\\s*$", RegexOption.IGNORE_CASE))
        }) {
            log("任务完成!"); OverlayService.update("完成!"); delay(1500); OverlayService.hide(context)
            updateState { copy(isRunning = false, isCompleted = true) }; bringAppToFront()
            return AgentResult(success = true, message = "任务完成")
        }

        return null
    }

    /** Executor 阶段。返回 null 表示应 continue */
    private suspend fun runExecutorPhase(vlm: VLMClient, infoPool: InfoPool, screenshot: Bitmap): ExecutorOutput? {
        log("Executor 决策中...")
        if (!_state.value.isRunning) return null

        val memory = infoPool.executorMemory
        val actionResponse = if (memory != null) {
            memory.addUserMessage(executor.getPrompt(infoPool), screenshot)
            log("记忆消息数: ${memory.size()}, 估算 token: ${memory.estimateTokens()}")
            val r = vlm.predictWithContext(memory.toMessagesJson()); memory.stripLastUserImage(); r
        } else { vlm.predict(executor.getPrompt(infoPool), listOf(screenshot)) }

        if (!_state.value.isRunning) return null
        if (actionResponse.isFailure) { log("Executor 调用失败: ${actionResponse.exceptionOrNull()?.message}"); return null }

        val text = actionResponse.getOrThrow()
        val result = executor.parseResponse(text)
        memory?.addAssistantMessage(text)
        val action = result.action

        log("思考: ${result.thought.take(80)}...")
        log("动作: ${result.actionStr}")
        log("描述: ${result.description}")
        infoPool.lastActionThought = result.thought; infoPool.lastSummary = result.description

        if (action == null) {
            log("动作解析失败")
            infoPool.actionHistory.add(Action(type = "invalid"))
            infoPool.summaryHistory.add(result.description)
            infoPool.actionOutcomes.add("C"); infoPool.errorDescriptions.add("Invalid action format")
            return null
        }

        // 特殊处理: answer / terminate
        if (action.type == "answer") {
            log("回答: ${action.text}"); OverlayService.update("${action.text?.take(20)}..."); delay(1500)
            OverlayService.hide(context); updateState { copy(isRunning = false, isCompleted = true, answer = action.text) }
            bringAppToFront(); return null
        }
        if (action.type == "terminate") {
            val ok = action.status == "success"
            log("任务${if (ok) "完成" else "失败"}"); OverlayService.update(if (ok) "完成!" else "失败"); delay(1500)
            OverlayService.hide(context); updateState { copy(isRunning = false, isCompleted = ok) }
            bringAppToFront(); return null
        }

        return ExecutorOutput(result, action, text)
    }

    /** 敏感操作确认。返回 true 继续执行，false 表示已取消 */
    private suspend fun confirmSensitiveAction(action: Action, result: ExecutorResult, infoPool: InfoPool): Boolean {
        if (action.needConfirm || action.message != null && action.type in listOf("click", "double_tap", "long_press")) {
            val msg = action.message ?: "确认执行此操作？"
            log("⚠️ 敏感操作: $msg")
            val ok = withContext(Dispatchers.Main) { AgentLoopHelper.waitForUserConfirm(msg) }
            if (!ok) {
                log("❌ 用户取消操作"); infoPool.actionHistory.add(action)
                infoPool.summaryHistory.add("用户取消: ${result.description}")
                infoPool.actionOutcomes.add("C"); infoPool.errorDescriptions.add("User cancelled")
                return false
            }
            log("✅ 用户确认，继续执行")
        }
        return true
    }

    /** Reflector 反思阶段 */
    private suspend fun runReflectorPhase(
        vlm: VLMClient, infoPool: InfoPool, screenshot: Bitmap, afterScreenshot: Bitmap
    ): ReflectorResult {
        log("Reflector 反思中...")
        if (!_state.value.isRunning) return ReflectorResult("C", "User stopped")
        val resp = vlm.predict(reflector.getPrompt(infoPool), listOf(screenshot, afterScreenshot))
        val r = if (resp.isSuccess) reflector.parseResponse(resp.getOrThrow()) else ReflectorResult("C", "Failed to call reflector")
        log("结果: ${r.outcome} - ${r.errorDescription.take(50)}"); return r
    }

    /** Notetaker 阶段 */
    private suspend fun runNotetakerPhase(vlm: VLMClient, infoPool: InfoPool, afterScreenshot: Bitmap) {
        log("Notetaker 记录中...")
        if (!_state.value.isRunning) return
        val resp = vlm.predict(notetaker.getPrompt(infoPool), listOf(afterScreenshot))
        if (resp.isSuccess) infoPool.importantNotes = notetaker.parseResponse(resp.getOrThrow())
    }

    // ==================== Screenshot helpers ====================

    private suspend fun takeScreenshot(): Bitmap? {
        log("截图中..."); OverlayService.setVisible(false); delay(100)
        val result = controller.screenshotWithFallback(); OverlayService.setVisible(true)
        if (result.isSensitive) {
            log("⚠️ 检测到敏感页面（截图被阻止），请求人工接管")
            val ok = withContext(Dispatchers.Main) { AgentLoopHelper.waitForUserConfirm("检测到敏感页面，是否继续执行？") }
            if (!ok) { log("用户取消，任务终止"); OverlayService.hide(context); bringAppToFront(); return null }
            log("用户确认继续（使用黑屏占位图）")
        } else if (result.isFallback) { log("⚠️ 截图失败，使用黑屏占位图继续") }
        return result.bitmap
    }

    private suspend fun takeScreenshotForReflection(): Bitmap {
        OverlayService.setVisible(false); delay(100)
        val result = controller.screenshotWithFallback(); OverlayService.setVisible(true)
        if (result.isFallback) log("动作后截图失败，使用黑屏占位图")
        return result.bitmap
    }

    // ==================== Shared utilities ====================

    private fun userStopped(): AgentResult {
        log("用户停止执行"); OverlayService.hide(context); bringAppToFront()
        return AgentResult(success = false, message = "用户停止")
    }

    private suspend fun maxStepsReached(): AgentResult {
        log("达到最大步数限制"); OverlayService.update("达到最大步数"); delay(1500)
        OverlayService.hide(context); updateState { copy(isRunning = false, isCompleted = false) }; bringAppToFront()
        return AgentResult(success = false, message = "达到最大步数限制")
    }

    private fun bringAppToFront() = AgentLoopHelper.bringAppToFront(context, ::log)

    private fun log(message: String) {
        Timber.d(message); _logs.value = _logs.value + message
    }

    private fun updateState(update: AgentState.() -> AgentState) {
        _state.value = _state.value.update()
    }
}

/** Executor 阶段的内部输出 */
private data class ExecutorOutput(
    val executorResult: ExecutorResult,
    val action: Action,
    val responseText: String
)
