package com.ai.mobileagent.engine

import android.content.Context
import com.ai.mobileagent.data.ActionType
import com.ai.mobileagent.data.AgentDecision
import com.ai.mobileagent.data.AgentLog
import com.ai.mobileagent.data.AppPreferences
import com.ai.mobileagent.gemini.GeminiClient
import com.ai.mobileagent.service.AgentAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AgentStatus {
    object Idle : AgentStatus()
    data class Running(val step: Int, val maxSteps: Int, val currentThought: String) : AgentStatus()
    data class Completed(val message: String) : AgentStatus()
    data class Failed(val reason: String) : AgentStatus()
    object Stopped : AgentStatus()
}

class AgentEngine(
    private val context: Context,
    private val preferences: AppPreferences = AppPreferences(context),
    private val geminiClient: GeminiClient = GeminiClient(),
    private val actionExecutor: ActionExecutor = ActionExecutor(context),
    private val safetyGuard: SafetyGuard = SafetyGuard()
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var agentJob: Job? = null

    private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    val status = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<AgentLog>>(emptyList())
    val logs = _logs.asStateFlow()

    fun isRunning(): Boolean = _status.value is AgentStatus.Running

    fun startTask(goal: String) {
        if (isRunning()) {
            stopTask()
        }

        agentJob = scope.launch {
            runAgentLoop(goal)
        }
    }

    fun stopTask() {
        agentJob?.cancel()
        agentJob = null
        safetyGuard.reset()
        _status.value = AgentStatus.Stopped
        addLog(0, "Emergency Stop Triggered", "User aborted execution")
    }

    private suspend fun runAgentLoop(goal: String) {
        val apiKey = preferences.geminiApiKey
        if (apiKey.isBlank()) {
            _status.value = AgentStatus.Failed("Gemini API Key is not set. Please enter it in Settings.")
            return
        }

        val service = AgentAccessibilityService.instance
        if (service == null) {
            _status.value = AgentStatus.Failed("Accessibility Service is disabled. Please enable it in Settings.")
            return
        }

        val maxSteps = preferences.maxSteps
        val stepDelay = preferences.stepDelayMs
        val history = mutableListOf<String>()
        safetyGuard.reset()

        addLog(0, "Initiating Task", "Goal: \"$goal\"")

        for (step in 1..maxSteps) {
            try {
                _status.value = AgentStatus.Running(step, maxSteps, "Observing screen...")

                // 1. Observe Screen
                val withScreenshot = preferences.useMultimodalVision
                val screenshotBase64 = if (withScreenshot) service.captureScreenshotBase64() else null
                val snapshot = service.captureCurrentScreen(false).copy(screenshotBase64 = screenshotBase64)

                _status.value = AgentStatus.Running(step, maxSteps, "Thinking with Gemini...")

                // 2. Query Gemini
                val decisionResult = geminiClient.getNextAction(
                    apiKey = apiKey,
                    model = preferences.selectedModel,
                    goal = goal,
                    history = history,
                    snapshot = snapshot
                )

                if (decisionResult.isFailure) {
                    val err = decisionResult.exceptionOrNull()?.message ?: "Unknown API error"
                    _status.value = AgentStatus.Failed("Gemini Error: $err")
                    addLog(step, "Gemini Error", err)
                    return
                }

                val decision = decisionResult.getOrThrow()
                _status.value = AgentStatus.Running(step, maxSteps, decision.thought)
                addLog(step, decision.thought, "${decision.action} ${decision.target_id ?: ""} ${decision.text ?: ""}".trim())

                // 3. Safety Check
                when (val safetyResult = safetyGuard.evaluateSafety(decision, snapshot)) {
                    is SafetyCheckResult.Blocked -> {
                        _status.value = AgentStatus.Failed(safetyResult.reason)
                        addLog(step, "Safety Block", safetyResult.reason)
                        return
                    }
                    is SafetyCheckResult.Allowed -> { /* Proceed */ }
                }

                // 4. Check for completion or failure signals
                if (decision.is_finished || decision.action == ActionType.FINISH) {
                    val finalMsg = decision.message ?: "Task completed successfully!"
                    _status.value = AgentStatus.Completed(finalMsg)
                    addLog(step, "Finished", finalMsg)
                    return
                }

                if (decision.action == ActionType.FAIL) {
                    val failMsg = decision.message ?: "Task cannot be completed."
                    _status.value = AgentStatus.Failed(failMsg)
                    addLog(step, "Failed", failMsg)
                    return
                }

                // 5. Execute Action
                val execution = actionExecutor.execute(decision)
                val historyEntry = "Step $step: Thought: \"${decision.thought}\" -> Action: ${decision.action} -> Result: ${execution.message}"
                history.add(historyEntry)

                // 6. UI stabilization delay
                delay(stepDelay)

            } catch (e: CancellationException) {
                _status.value = AgentStatus.Stopped
                return
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Unexpected error"
                _status.value = AgentStatus.Failed("Execution Error: $err")
                addLog(step, "Exception", err)
                return
            }
        }

        // Exceeded max steps
        val timeoutMsg = "Reached maximum limit of $maxSteps steps without completing the goal."
        _status.value = AgentStatus.Failed(timeoutMsg)
        addLog(maxSteps, "Timeout", timeoutMsg)
    }

    private fun addLog(step: Int, thought: String, actionDesc: String) {
        val current = _logs.value.toMutableList()
        current.add(AgentLog(step, thought, actionDesc))
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    companion object {
        @Volatile
        private var instance: AgentEngine? = null

        fun getInstance(context: Context): AgentEngine {
            return instance ?: synchronized(this) {
                instance ?: AgentEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
