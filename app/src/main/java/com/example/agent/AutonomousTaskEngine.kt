package com.example.agent

import com.example.browser.TwoLayerBrowserController
import com.example.data.local.AutonomousTaskEntity
import com.example.data.local.OpenMuseRepository
import com.example.data.local.TaskStepEntity
import com.example.proot.AndroidProotComputerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 7 — Autonomous Task Engine
 * Implements the explicit 8-stage execution cycle:
 * 1. Understand the request
 * 2. Build a plan
 * 3. Select an approved tool
 * 4. Execute the tool (with human checkpoint gating for consequential operations)
 * 5. Collect real observations
 * 6. Decide whether another step is needed (within maxStepsBudget)
 * 7. Save state to Room SQLite
 * 8. Report completed result or precise failure
 */
class AutonomousTaskEngine(
    private val repository: OpenMuseRepository,
    private val computerProvider: AndroidProotComputerProvider,
    private val browserController: TwoLayerBrowserController,
    private val geminiProvider: GeminiModelProvider,
    private val scope: CoroutineScope
) {
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val _runningTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val runningTaskIds: StateFlow<Set<Long>> = _runningTaskIds.asStateFlow()

    suspend fun startNewTask(
        threadId: Long,
        goal: String,
        maxStepsBudget: Int = 8,
        timeoutMs: Long = 120_000L
    ): Long {
        val taskId = repository.createTask(
            threadId = threadId,
            goal = goal,
            maxStepsBudget = maxStepsBudget,
            timeoutMs = timeoutMs
        )
        launchTaskExecution(taskId, resumeFromStepIndex = 0, userApprovedCurrentStep = false)
        return taskId
    }

    fun launchTaskExecution(
        taskId: Long,
        resumeFromStepIndex: Int = 0,
        userApprovedCurrentStep: Boolean = false
    ) {
        activeJobs[taskId]?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            markTaskRunning(taskId, true)
            try {
                val task = repository.getTask(taskId) ?: return@launch
                withTimeout(task.timeoutMs) {
                    runTaskCycle(task, resumeFromStepIndex, userApprovedCurrentStep)
                }
            } catch (ce: CancellationException) {
                val current = repository.getTask(taskId)
                if (current != null && current.status != "PAUSED" && current.status != "CANCELLED") {
                    repository.updateTask(
                        current.copy(
                            status = "CANCELLED",
                            errorMessage = "Task cancelled or timed out."
                        )
                    )
                }
            } catch (e: Exception) {
                val current = repository.getTask(taskId)
                if (current != null) {
                    repository.updateTask(
                        current.copy(
                            status = "FAILED",
                            errorMessage = e.message ?: "Unexpected execution failure"
                        )
                    )
                    repository.addMessage(
                        threadId = current.threadId,
                        role = "ASSISTANT",
                        content = "Task #${current.id} failed: ${e.message}",
                        cardType = "TEXT"
                    )
                }
            } finally {
                markTaskRunning(taskId, false)
                activeJobs.remove(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    private suspend fun runTaskCycle(
        initialTask: AutonomousTaskEntity,
        resumeFromStepIndex: Int,
        userApprovedCurrentStep: Boolean
    ) {
        var task = initialTask
        var existingSteps = repository.getTaskSteps(task.id)

        // Stage 1 & 2: Understand request & build plan if not already planned
        if (existingSteps.isEmpty()) {
            repository.updateTask(task.copy(status = "PLANNING"))
            val planResult = geminiProvider.buildExecutionPlan(task.goal)
            if (planResult.isFailure) {
                val errMsg = planResult.exceptionOrNull()?.message ?: "Unable to build execution plan"
                repository.updateTask(task.copy(status = "FAILED", errorMessage = errMsg))
                repository.addMessage(
                    threadId = task.threadId,
                    role = "ASSISTANT",
                    content = "Task planning halted: $errMsg",
                    cardType = "TEXT"
                )
                return
            }

            val plannedSteps = planResult.getOrDefault(emptyList()).take(task.maxStepsBudget)

            // If this is a direct conversational reply (chat_reply), post the assistant reply directly without noisy plan/receipt cards
            if (plannedSteps.size == 1 && plannedSteps.first().toolName == "chat_reply") {
                val replyText = plannedSteps.first().argument
                repository.insertTaskStep(
                    TaskStepEntity(
                        taskId = task.id,
                        stepIndex = 0,
                        title = plannedSteps.first().title,
                        toolName = "chat_reply",
                        toolArgsJson = "conversational_response",
                        requiresApproval = false,
                        status = "COMPLETED",
                        observationOutput = replyText,
                        exitCode = 0,
                        durationMs = 12L
                    )
                )
                repository.updateTask(
                    task.copy(
                        status = "COMPLETED",
                        finalSummary = replyText,
                        errorMessage = null
                    )
                )
                repository.addMessage(
                    threadId = task.threadId,
                    role = "ASSISTANT",
                    content = replyText,
                    cardType = "TEXT"
                )
                return
            }

            plannedSteps.forEachIndexed { idx, p ->
                val approvalReason = if (p.toolName == "run_command" || p.toolName == "git_op") {
                    computerProvider.inspectConsequentialCommand("${p.toolName} ${p.argument}")
                } else null

                repository.insertTaskStep(
                    TaskStepEntity(
                        taskId = task.id,
                        stepIndex = idx,
                        title = p.title,
                        toolName = p.toolName,
                        toolArgsJson = p.argument,
                        requiresApproval = approvalReason != null,
                        approvalReason = approvalReason,
                        status = "PENDING"
                    )
                )
            }
            existingSteps = repository.getTaskSteps(task.id)

            val planSummary = existingSteps.joinToString("\n") { step ->
                "${step.stepIndex + 1}. [${step.toolName}] ${step.title} (${step.toolArgsJson})" +
                    if (step.requiresApproval) " ⚠️ [Requires Approval]" else ""
            }
            repository.addMessage(
                threadId = task.threadId,
                role = "ASSISTANT",
                content = "Execution Plan for Task #${task.id}:\n$planSummary",
                cardType = "PLAN",
                metadataJson = """{"taskId":${task.id}}"""
            )
        }

        task = repository.getTask(task.id) ?: return
        repository.updateTask(task.copy(status = "RUNNING", errorMessage = null))

        // Stage 3..7: Iterate through planned steps within budget
        val steps = repository.getTaskSteps(task.id)
        for (step in steps) {
            if (step.stepIndex < resumeFromStepIndex || step.status == "COMPLETED") continue

            val latestTask = repository.getTask(task.id) ?: return
            if (latestTask.status == "PAUSED" || latestTask.status == "CANCELLED") return

            val isApprovedNow = (step.stepIndex == resumeFromStepIndex && userApprovedCurrentStep)
            if (step.requiresApproval && !isApprovedNow) {
                repository.updateTaskStep(step.copy(status = "AWAITING_APPROVAL"))
                repository.updateTask(
                    latestTask.copy(
                        status = "AWAITING_APPROVAL",
                        currentStepIndex = step.stepIndex
                    )
                )
                repository.addMessage(
                    threadId = latestTask.threadId,
                    role = "ASSISTANT",
                    content = "Approval Required for Step ${step.stepIndex + 1}: ${step.title}\nCommand/Arg: `${step.toolArgsJson}`\nReason: ${step.approvalReason}",
                    cardType = "APPROVAL_REQUEST",
                    metadataJson = """{"taskId":${latestTask.id},"stepIndex":${step.stepIndex}}"""
                )
                return
            }

            repository.updateTaskStep(step.copy(status = "RUNNING"))
            repository.updateTask(latestTask.copy(currentStepIndex = step.stepIndex, status = "RUNNING"))

            val stepOutcome = executeSingleToolStep(step, confirmedByUser = isApprovedNow || !step.requiresApproval)
            repository.updateTaskStep(stepOutcome)

            // Post concise inline receipt card to chat thread
            val cardType = when (step.toolName) {
                "browse_url", "web_search" -> "BROWSER_SNAPSHOT"
                "write_file", "read_file" -> "FILE_DIFF"
                else -> "COMMAND_RECEIPT"
            }
            val safeUrlMeta = browserController.uiState.value.currentUrl.replace("\"", "")
            val cleanCardBody = stepOutcome.observationOutput
                .replace(Regex("\\[UNTRUSTED_WEB_CONTENT_START[^\\]]*\\]"), "")
                .replace("[UNTRUSTED_WEB_CONTENT_END]", "")
                .trim()
                .lines()
                .filterNot { it.trim().startsWith("Interactive Elements") || it.trim().startsWith("- LINK [") }
                .take(10)
                .joinToString("\n")

            repository.addMessage(
                threadId = latestTask.threadId,
                role = "TOOL",
                content = "**[Step ${step.stepIndex + 1}: ${step.toolName}]** ${step.title}\n$cleanCardBody",
                cardType = cardType,
                metadataJson = """{"taskId":${latestTask.id},"stepIndex":${step.stepIndex},"exitCode":${stepOutcome.exitCode ?: 0},"durationMs":${stepOutcome.durationMs},"url":"$safeUrlMeta"}"""
            )

            if (stepOutcome.status == "FAILED") {
                val updatedTask = repository.getTask(task.id) ?: return
                if (updatedTask.retryCount < updatedTask.maxRetries) {
                    repository.updateTask(updatedTask.copy(retryCount = updatedTask.retryCount + 1))
                } else {
                    repository.updateTask(
                        updatedTask.copy(
                            status = "FAILED",
                            errorMessage = "Step ${step.stepIndex + 1} (${step.toolName}) failed: ${stepOutcome.observationOutput.take(200)}"
                        )
                    )
                    return
                }
            }
        }

        // Stage 8: Synthesize real observations & complete task
        val completedSteps = repository.getTaskSteps(task.id)
        val observationsText = completedSteps.joinToString("\n\n") { s ->
            "Step ${s.stepIndex + 1} (${s.toolName} ${s.toolArgsJson}) [exit=${s.exitCode}, ${s.durationMs}ms]:\n${s.observationOutput}"
        }

        val finalTask = repository.getTask(task.id) ?: return
        var streamedReply = ""
        val synthResult = geminiProvider.synthesizeObservationsStream(
            goal = finalTask.goal,
            observationsSummary = observationsText
        ) { chunk ->
            streamedReply += chunk
        }

        val summary = synthResult.getOrDefault(
            streamedReply.ifBlank { "Completed ${completedSteps.size} verified local step(s).\n\n$observationsText" }
        )

        repository.updateTask(
            finalTask.copy(
                status = "COMPLETED",
                finalSummary = summary,
                errorMessage = null
            )
        )
        repository.addMessage(
            threadId = finalTask.threadId,
            role = "ASSISTANT",
            content = summary,
            cardType = "TEXT"
        )
    }

    private suspend fun executeSingleToolStep(
        step: TaskStepEntity,
        confirmedByUser: Boolean
    ): TaskStepEntity {
        val start = System.currentTimeMillis()
        return when (step.toolName) {
            "run_command" -> {
                val res = computerProvider.executeShellLine(
                    rawCommandLine = step.toolArgsJson,
                    confirmedByUser = confirmedByUser
                )
                if (res.requiresConfirmation) {
                    step.copy(
                        status = "AWAITING_APPROVAL",
                        requiresApproval = true,
                        approvalReason = res.confirmationReason,
                        observationOutput = res.stderr
                    )
                } else {
                    val combined = buildString {
                        if (res.stdout.isNotBlank()) appendLine(res.stdout.trim())
                        if (res.stderr.isNotBlank()) appendLine("STDERR: ${res.stderr.trim()}")
                        append("Exit Code: ${res.exitCode} | Mode: ${res.executionMode} | Duration: ${res.durationMs}ms")
                    }
                    step.copy(
                        status = if (res.exitCode == 0) "COMPLETED" else "FAILED",
                        observationOutput = combined,
                        exitCode = res.exitCode,
                        durationMs = res.durationMs
                    )
                }
            }
            "write_file" -> {
                val parts = step.toolArgsJson.split("|", limit = 2)
                val relPath = parts.getOrNull(0)?.trim().takeUnless { it.isNullOrBlank() } ?: "agent_note.txt"
                val content = parts.getOrNull(1) ?: step.toolArgsJson
                val writeRes = computerProvider.writeWorkspaceFile(relPath, content)
                val dur = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                writeRes.fold(
                    onSuccess = { file ->
                        step.copy(
                            status = "COMPLETED",
                            observationOutput = "Wrote ${file.length()} bytes to workspace file '${relPath}'.",
                            exitCode = 0,
                            durationMs = dur
                        )
                    },
                    onFailure = { err ->
                        step.copy(
                            status = "FAILED",
                            observationOutput = "File write error: ${err.message}",
                            exitCode = 1,
                            durationMs = dur
                        )
                    }
                )
            }
            "read_file" -> {
                val relPath = step.toolArgsJson.trim()
                val readRes = computerProvider.readWorkspaceFile(relPath)
                val dur = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                readRes.fold(
                    onSuccess = { text ->
                        step.copy(
                            status = "COMPLETED",
                            observationOutput = "Contents of '$relPath' (${text.length} chars):\n${text.take(2000)}",
                            exitCode = 0,
                            durationMs = dur
                        )
                    },
                    onFailure = { err ->
                        step.copy(
                            status = "FAILED",
                            observationOutput = "File read error: ${err.message}",
                            exitCode = 1,
                            durationMs = dur
                        )
                    }
                )
            }
            "list_files" -> {
                val items = computerProvider.listWorkspaceFiles(step.toolArgsJson.trim())
                val dur = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val listing = if (items.isEmpty()) {
                    "Workspace directory is empty."
                } else {
                    items.joinToString("\n") {
                        "${if (it.isDirectory) "[DIR] " else "[FILE]"} ${it.relativePath} (${it.sizeBytes} bytes)"
                    }
                }
                step.copy(
                    status = "COMPLETED",
                    observationOutput = listing,
                    exitCode = 0,
                    durationMs = dur
                )
            }
            "git_op" -> {
                val tokens = step.toolArgsJson.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val op = tokens.firstOrNull() ?: "status"
                val extraArgs = tokens.drop(1)
                val res = computerProvider.executeGitOperation(op, extraArgs, confirmedByUser = confirmedByUser)
                step.copy(
                    status = if (res.exitCode == 0) "COMPLETED" else "FAILED",
                    observationOutput = (res.stdout + "\n" + res.stderr).trim(),
                    exitCode = res.exitCode,
                    durationMs = res.durationMs
                )
            }
            "browse_url" -> {
                val dom = browserController.inspectUrlAutonomously(step.toolArgsJson.trim())
                val dur = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val summary = buildString {
                    appendLine("URL: ${dom.url} (HTTP ${dom.statusCode})")
                    appendLine("Title: ${dom.title}")
                    appendLine("Interactive Elements (${dom.interactiveElementsSummary.size}):")
                    dom.interactiveElementsSummary.take(6).forEach { appendLine("  - $it") }
                    appendLine(dom.sanitizedUntrustedText.take(1500))
                }
                step.copy(
                    status = if (dom.error == null) "COMPLETED" else "FAILED",
                    observationOutput = summary,
                    exitCode = if (dom.error == null) 0 else 1,
                    durationMs = dur
                )
            }
            "web_search" -> {
                val dom = browserController.searchWebAutonomously(step.toolArgsJson.trim())
                val dur = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val summary = buildString {
                    appendLine("Search Query: ${step.toolArgsJson.trim()}")
                    appendLine("Browser URL: ${dom.url}")
                    appendLine(dom.sanitizedUntrustedText.take(2500))
                }
                step.copy(
                    status = if (dom.error == null) "COMPLETED" else "FAILED",
                    observationOutput = summary,
                    exitCode = if (dom.error == null) 0 else 1,
                    durationMs = dur
                )
            }
            "chat_reply" -> {
                step.copy(
                    status = "COMPLETED",
                    observationOutput = step.toolArgsJson,
                    exitCode = 0,
                    durationMs = 5L
                )
            }
            else -> {
                val res = computerProvider.executeShellLine(step.toolArgsJson, confirmedByUser = confirmedByUser)
                step.copy(
                    status = if (res.exitCode == 0) "COMPLETED" else "FAILED",
                    observationOutput = (res.stdout + "\n" + res.stderr).trim(),
                    exitCode = res.exitCode,
                    durationMs = res.durationMs
                )
            }
        }
    }

    suspend fun pauseTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        val task = repository.getTask(taskId) ?: return
        repository.updateTask(task.copy(status = "PAUSED"))
    }

    suspend fun resumeTask(taskId: Long) {
        val task = repository.getTask(taskId) ?: return
        launchTaskExecution(taskId, resumeFromStepIndex = task.currentStepIndex, userApprovedCurrentStep = false)
    }

    suspend fun approveTaskCheckpoint(taskId: Long, stepIndex: Int) {
        launchTaskExecution(taskId, resumeFromStepIndex = stepIndex, userApprovedCurrentStep = true)
    }

    suspend fun rejectTaskCheckpoint(taskId: Long, stepIndex: Int) {
        activeJobs[taskId]?.cancel()
        val task = repository.getTask(taskId) ?: return
        val steps = repository.getTaskSteps(taskId)
        steps.firstOrNull { it.stepIndex == stepIndex }?.let { step ->
            repository.updateTaskStep(step.copy(status = "SKIPPED", observationOutput = "Rejected by user at checkpoint."))
        }
        repository.updateTask(
            task.copy(
                status = "CANCELLED",
                errorMessage = "User rejected approval checkpoint at step ${stepIndex + 1}."
            )
        )
        repository.addMessage(
            threadId = task.threadId,
            role = "SYSTEM",
            content = "Task #${task.id} halted: User rejected checkpoint at step ${stepIndex + 1}.",
            cardType = "TEXT"
        )
    }

    suspend fun cancelTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        computerProvider.cancelAllRunningCommands()
        val task = repository.getTask(taskId) ?: return
        repository.updateTask(task.copy(status = "CANCELLED", errorMessage = "Cancelled by user."))
    }

    suspend fun retryTask(taskId: Long) {
        val task = repository.getTask(taskId) ?: return
        repository.updateTask(task.copy(status = "RUNNING", errorMessage = null))
        launchTaskExecution(taskId, resumeFromStepIndex = task.currentStepIndex, userApprovedCurrentStep = false)
    }

    private fun markTaskRunning(taskId: Long, running: Boolean) {
        _runningTaskIds.value = if (running) {
            _runningTaskIds.value + taskId
        } else {
            _runningTaskIds.value - taskId
        }
    }
}
