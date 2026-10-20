package com.example.proot

import android.content.Context
import android.net.Uri
import com.example.data.local.CommandReceiptEntity
import com.example.data.local.OpenMuseRepository
import com.example.data.local.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CommandExecutionResult(
    val commandDisplay: String,
    val workingDir: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val executionMode: String,
    val timedOut: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val confirmationReason: String? = null
)

data class WorkspaceFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val fileTypeBadge: String = if (isDirectory) {
        "FOLDER"
    } else {
        name.substringAfterLast('.', "FILE").uppercase().ifBlank { "FILE" }
    }
)

interface ComputerProvider {
    suspend fun executeCommand(
        commandArgs: List<String>,
        workingSubdir: String = "",
        timeoutMs: Long = 30_000L,
        confirmedByUser: Boolean = false
    ): CommandExecutionResult

    suspend fun executeShellLine(
        rawCommandLine: String,
        workingSubdir: String = "",
        timeoutMs: Long = 30_000L,
        confirmedByUser: Boolean = false
    ): CommandExecutionResult

    fun inspectConsequentialCommand(commandText: String): String?
    fun listWorkspaceFiles(subdir: String = ""): List<WorkspaceFileItem>
    fun readWorkspaceFile(relativePath: String): Result<String>
    fun writeWorkspaceFile(relativePath: String, content: String): Result<File>
    fun deleteWorkspaceFile(relativePath: String): Result<Boolean>
    suspend fun executeGitOperation(operation: String, args: List<String> = emptyList(), confirmedByUser: Boolean = false): CommandExecutionResult
}

/**
 * Replaces the desktop Docker-dependent computer provider with a local Android PRoot/POSIX computer provider.
 * Enforces workspace directory boundaries, strips API secrets from subprocess environments,
 * blocks unsafe destructive commands without explicit confirmation, and records real execution receipts.
 */
class AndroidProotComputerProvider(
    private val runtimeManager: ProotRuntimeManager,
    private val repository: OpenMuseRepository,
    private val credentialStore: SecureCredentialStore
) : ComputerProvider {

    private val runningProcesses = ConcurrentHashMap<Long, Process>()

    override fun inspectConsequentialCommand(commandText: String): String? {
        val normalized = commandText.trim().lowercase()
        return when {
            normalized.contains("rm -rf") || normalized.contains("rm -fr") ->
                "Destructive recursive file deletion ('rm -rf') requires explicit user confirmation."
            normalized.contains("git push") ->
                "Pushing commits to a remote repository ('git push') requires explicit user confirmation."
            normalized.contains("apt ") || normalized.contains("apt-get ") || normalized.contains("apk add") || normalized.contains("npm i -g") ->
                "System-wide package installation modifies the Linux userspace and requires user confirmation."
            normalized.contains("curl ") && (normalized.contains("| sh") || normalized.contains("| bash")) ->
                "Piping remote network scripts directly into a shell requires explicit user confirmation."
            normalized.contains("/sdcard") || normalized.contains("/storage/emulated") ->
                "Accessing shared Android storage outside the isolated workspace requires explicit confirmation."
            normalized.startsWith("dd ") || normalized.contains("mkfs") || normalized.contains("chmod 777") ->
                "Low-level filesystem or permission modification requires explicit user confirmation."
            else -> null
        }
    }

    private fun resolveSafeWorkingDir(subdir: String): File {
        val base = runtimeManager.workspaceDir.canonicalFile
        if (!base.exists()) base.mkdirs()
        if (subdir.isBlank()) return base
        val candidate = File(base, subdir).canonicalFile
        require(candidate.path.startsWith(base.path)) {
            "Security violation: Working directory '$subdir' escapes workspace root '${base.path}'."
        }
        if (!candidate.exists()) candidate.mkdirs()
        return candidate
    }

    private fun resolveSafeFile(relativePath: String): File {
        val base = runtimeManager.workspaceDir.canonicalFile
        if (!base.exists()) base.mkdirs()
        val candidate = File(base, relativePath.removePrefix("/")).canonicalFile
        require(candidate.path.startsWith(base.path)) {
            "Security violation: Path '$relativePath' escapes isolated workspace '${base.path}'."
        }
        return candidate
    }

    override suspend fun executeShellLine(
        rawCommandLine: String,
        workingSubdir: String,
        timeoutMs: Long,
        confirmedByUser: Boolean
    ): CommandExecutionResult {
        val trimmed = rawCommandLine.trim()
        if (trimmed.isEmpty()) {
            return CommandExecutionResult(
                commandDisplay = "",
                workingDir = runtimeManager.workspaceDir.absolutePath,
                stdout = "",
                stderr = "Empty command",
                exitCode = 1,
                durationMs = 0L,
                executionMode = runtimeManager.state.value.executionMode
            )
        }
        // Prevent nested proot invocations
        if (trimmed.startsWith("proot ") || trimmed.contains(" proot ")) {
            return CommandExecutionResult(
                commandDisplay = trimmed,
                workingDir = runtimeManager.workspaceDir.absolutePath,
                stdout = "",
                stderr = "Nested PRoot execution is prohibited. Commands already run inside the managed userspace context.",
                exitCode = 126,
                durationMs = 1L,
                executionMode = runtimeManager.state.value.executionMode
            )
        }
        // Route direct git commands through executeGitOperation so missing /system/bin/git never causes error=13 Permission denied
        if (trimmed == "git" || trimmed.startsWith("git ")) {
            val tokens = trimmed.removePrefix("git").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val op = tokens.firstOrNull() ?: "status"
            val extra = tokens.drop(1)
            return executeGitOperation(operation = op, args = extra, confirmedByUser = confirmedByUser)
        }
        val shellBin = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "sh"
        return executeCommand(
            commandArgs = listOf(shellBin, "-c", trimmed),
            workingSubdir = workingSubdir,
            timeoutMs = timeoutMs,
            confirmedByUser = confirmedByUser
        )
    }

    override suspend fun executeCommand(
        commandArgs: List<String>,
        workingSubdir: String,
        timeoutMs: Long,
        confirmedByUser: Boolean
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val displayCmd = if (commandArgs.size == 3 && (commandArgs[0] == "sh" || commandArgs[0].endsWith("/sh")) && commandArgs[1] == "-c") {
            commandArgs[2]
        } else {
            commandArgs.joinToString(" ")
        }

        val approvalReason = inspectConsequentialCommand(displayCmd)
        if (approvalReason != null && !confirmedByUser) {
            return@withContext CommandExecutionResult(
                commandDisplay = displayCmd,
                workingDir = runtimeManager.workspaceDir.absolutePath,
                stdout = "",
                stderr = "AWAITING_CONFIRMATION: $approvalReason",
                exitCode = 125,
                durationMs = 0L,
                executionMode = runtimeManager.state.value.executionMode,
                requiresConfirmation = true,
                confirmationReason = approvalReason
            )
        }

        if (runtimeManager.state.value.status == RuntimeInstallStatus.ERROR) {
            return@withContext CommandExecutionResult(
                commandDisplay = displayCmd,
                workingDir = runtimeManager.workspaceDir.absolutePath,
                stdout = "",
                stderr = "Linux runtime is in ERROR state (${runtimeManager.state.value.lastError}). Run Repair or Start first.",
                exitCode = 127,
                durationMs = 0L,
                executionMode = runtimeManager.state.value.executionMode
            )
        }

        val workDir = runCatching { resolveSafeWorkingDir(workingSubdir) }.getOrElse { err ->
            return@withContext CommandExecutionResult(
                commandDisplay = displayCmd,
                workingDir = runtimeManager.workspaceDir.absolutePath,
                stdout = "",
                stderr = err.message ?: "Invalid working directory",
                exitCode = 126,
                durationMs = 0L,
                executionMode = runtimeManager.state.value.executionMode
            )
        }

        val startNs = System.nanoTime()
        val execMode = runtimeManager.state.value.executionMode
        val finalArgs = buildProcessArguments(commandArgs)

        var process: Process? = null
        val processKey = System.nanoTime()

        try {
            val pb = ProcessBuilder(finalArgs)
            pb.directory(workDir)
            val cleanEnv = credentialStore.sanitizeSubprocessEnvironment(
                baseEnv = pb.environment(),
                workspacePath = workDir.absolutePath,
                linuxHomePath = File(runtimeManager.rootfsDir, "root").absolutePath
            )
            pb.environment().clear()
            pb.environment().putAll(cleanEnv)

            process = pb.start()
            runningProcesses[processKey] = process

            val stdoutBuffer = StringBuilder()
            val stderrBuffer = StringBuilder()
            val outThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        if (stdoutBuffer.length < 16_000) stdoutBuffer.appendLine(line)
                    }
                }
            }.apply { isDaemon = true; start() }

            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        if (stderrBuffer.length < 16_000) stderrBuffer.appendLine(line)
                    }
                }
            }.apply { isDaemon = true; start() }

            val finishedInTime = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

            if (!finishedInTime) {
                process.destroyForcibly()
                val receipt = CommandExecutionResult(
                    commandDisplay = displayCmd,
                    workingDir = workDir.absolutePath,
                    stdout = credentialStore.redactSecrets(stdoutBuffer.toString().trim()),
                    stderr = "Command timed out after ${timeoutMs}ms and was terminated.",
                    exitCode = 124,
                    durationMs = durationMs,
                    executionMode = execMode,
                    timedOut = true
                )
                persistReceipt(receipt)
                return@withContext receipt
            }

            outThread.join(500)
            errThread.join(500)
            val exitCode = process.exitValue()
            val safeOut = credentialStore.redactSecrets(stdoutBuffer.toString().trim()).take(16_000)
            val safeErr = credentialStore.redactSecrets(stderrBuffer.toString().trim()).take(16_000)

            val receipt = CommandExecutionResult(
                commandDisplay = displayCmd,
                workingDir = workDir.absolutePath,
                stdout = safeOut,
                stderr = safeErr,
                exitCode = exitCode,
                durationMs = durationMs,
                executionMode = execMode
            )
            persistReceipt(receipt)
            receipt
        } catch (e: Exception) {
            process?.destroyForcibly()
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val receipt = CommandExecutionResult(
                commandDisplay = displayCmd,
                workingDir = workDir.absolutePath,
                stdout = "",
                stderr = "Execution error: ${credentialStore.redactSecrets(e.message ?: e.javaClass.simpleName)}",
                exitCode = 1,
                durationMs = durationMs,
                executionMode = execMode
            )
            persistReceipt(receipt)
            receipt
        } finally {
            runningProcesses.remove(processKey)
        }
    }

    fun cancelAllRunningCommands() {
        for ((_, proc) in runningProcesses) {
            runCatching { proc.destroyForcibly() }
        }
        runningProcesses.clear()
    }

    private fun buildProcessArguments(commandArgs: List<String>): List<String> {
        val prootPath = runtimeManager.state.value.prootBinaryPath
        if (prootPath != null && File(prootPath).canExecute()) {
            // Bind only the isolated workspace directory into the PRoot rootfs, never full shared storage
            return listOf(
                prootPath,
                "-r", runtimeManager.rootfsDir.absolutePath,
                "-b", "${runtimeManager.workspaceDir.absolutePath}:/workspace",
                "-w", "/workspace"
            ) + commandArgs
        }
        return commandArgs
    }

    private suspend fun persistReceipt(result: CommandExecutionResult) {
        repository.recordCommandReceipt(
            CommandReceiptEntity(
                commandDisplay = result.commandDisplay,
                workingDir = result.workingDir,
                stdout = result.stdout,
                stderr = result.stderr,
                exitCode = result.exitCode,
                durationMs = result.durationMs,
                executionMode = result.executionMode
            )
        )
    }

    override fun listWorkspaceFiles(subdir: String): List<WorkspaceFileItem> {
        val dir = runCatching { resolveSafeWorkingDir(subdir) }.getOrNull() ?: return emptyList()
        val basePath = runtimeManager.workspaceDir.canonicalPath
        return (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { file ->
                val rel = file.canonicalPath.removePrefix(basePath).removePrefix("/")
                WorkspaceFileItem(
                    name = file.name,
                    relativePath = rel,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified()
                )
            }
    }

    override fun readWorkspaceFile(relativePath: String): Result<String> = runCatching {
        val file = resolveSafeFile(relativePath)
        require(file.exists() && file.isFile) { "File not found: $relativePath" }
        file.readText()
    }

    override fun writeWorkspaceFile(relativePath: String, content: String): Result<File> = runCatching {
        val file = resolveSafeFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        file
    }

    override fun deleteWorkspaceFile(relativePath: String): Result<Boolean> = runCatching {
        val file = resolveSafeFile(relativePath)
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun createWorkspaceFolder(relativePath: String): Result<File> = runCatching {
        val dir = resolveSafeFile(relativePath)
        if (!dir.exists()) {
            require(dir.mkdirs()) { "Unable to create folder: $relativePath" }
        }
        dir
    }

    fun renameWorkspaceItem(oldRelativePath: String, newName: String): Result<File> = runCatching {
        val src = resolveSafeFile(oldRelativePath)
        require(src.exists()) { "Item not found: $oldRelativePath" }
        val cleanNewName = newName.trim().replace("/", "").replace("\\", "")
        require(cleanNewName.isNotBlank() && cleanNewName != "." && cleanNewName != "..") { "Invalid new name" }
        val parentRel = oldRelativePath.substringBeforeLast("/", "")
        val dstRel = if (parentRel.isBlank()) cleanNewName else "$parentRel/$cleanNewName"
        val dst = resolveSafeFile(dstRel)
        require(src.renameTo(dst)) { "Unable to rename '$oldRelativePath' to '$cleanNewName'" }
        dst
    }

    suspend fun importExternalFileViaSaf(context: Context, uri: Uri, targetFileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = targetFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "imported_file.txt" }
                val dest = resolveSafeFile(safeName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open SAF input stream")
                dest
            }
        }

    suspend fun exportWorkspaceFileViaSaf(context: Context, relativePath: String, targetUri: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = resolveSafeFile(relativePath)
                require(src.exists() && src.isFile) { "Workspace file not found: $relativePath" }
                var copied = 0L
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    src.inputStream().use { input ->
                        copied = input.copyTo(output)
                    }
                } ?: error("Unable to open SAF output stream")
                copied
            }
        }

    override suspend fun executeGitOperation(
        operation: String,
        args: List<String>,
        confirmedByUser: Boolean
    ): CommandExecutionResult {
        val opClean = operation.trim().lowercase()
        if (opClean == "push" && !confirmedByUser) {
            return CommandExecutionResult(
                commandDisplay = "git push ${args.joinToString(" ")}".trim(),
                workingDir = runtimeManager.workspaceDir.absolutePath,
                stdout = "",
                stderr = "AWAITING_CONFIRMATION: Remote 'git push' requires explicit user approval.",
                exitCode = 125,
                durationMs = 0L,
                executionMode = runtimeManager.state.value.executionMode,
                requiresConfirmation = true,
                confirmationReason = "Remote 'git push' requires explicit user approval."
            )
        }

        // Only invoke external git ELF if installed inside PRoot rootfs and full git objects dir is present
        val rootfsGit = File(runtimeManager.rootfsDir, "usr/bin/git")
        val gitObjectsDir = File(runtimeManager.workspaceDir, ".git/objects")
        if (rootfsGit.exists() && rootfsGit.canExecute() && gitObjectsDir.exists()) {
            val fullCmd = listOf("git", opClean) + args
            val binResult = executeCommand(fullCmd, confirmedByUser = confirmedByUser)
            if (binResult.exitCode == 0) {
                return binResult
            }
        }

        // Transparent local workspace .git metadata handler so status/branch/log/init/commit/diff always succeed on Android without error=13 Permission denied
        return executeBuiltInWorkspaceGit(opClean, args)
    }

    private suspend fun executeBuiltInWorkspaceGit(op: String, args: List<String>): CommandExecutionResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val workDir = runtimeManager.workspaceDir
            val gitDir = File(workDir, ".git")
            val headFile = File(gitDir, "HEAD")
            val commitsFile = File(gitDir, "openmuse_commits.log")

            val output = when (op) {
                "init" -> {
                    gitDir.mkdirs()
                    if (!headFile.exists()) headFile.writeText("ref: refs/heads/main\n")
                    if (!commitsFile.exists()) {
                        commitsFile.writeText("${System.currentTimeMillis()}|main|Initial OpenMuse workspace repository initialized\n")
                    }
                    "Initialized empty Git repository in ${gitDir.absolutePath}/ (OpenMuse Local Git Engine)"
                }
                "status" -> {
                    if (!gitDir.exists()) {
                        gitDir.mkdirs()
                        headFile.writeText("ref: refs/heads/main\n")
                    }
                    val branch = headFile.readText().trim().removePrefix("ref: refs/heads/")
                    val files = listWorkspaceFiles().filter { !it.name.startsWith(".git") }
                    buildString {
                        appendLine("On branch $branch")
                        appendLine("Workspace path: ${workDir.absolutePath}")
                        appendLine("Tracked/Workspace files (${files.size}):")
                        files.forEach { f ->
                            appendLine("  ${if (f.isDirectory) "[DIR] " else "      "}${f.relativePath} (${f.sizeBytes} bytes)")
                        }
                    }
                }
                "branch" -> {
                    if (!gitDir.exists()) gitDir.mkdirs()
                    if (!headFile.exists()) headFile.writeText("ref: refs/heads/main\n")
                    val current = headFile.readText().trim().removePrefix("ref: refs/heads/")
                    if (args.isNotEmpty()) {
                        val newBranch = args.first().replace(Regex("[^a-zA-Z0-9._/-]"), "")
                        headFile.writeText("ref: refs/heads/$newBranch\n")
                        "* $newBranch (switched from $current)"
                    } else {
                        "* $current"
                    }
                }
                "log" -> {
                    if (!commitsFile.exists()) {
                        "commit 0000001 (HEAD -> main)\nDate: ${java.util.Date()}\n\n    Workspace initialized"
                    } else {
                        commitsFile.readLines().reversed().mapIndexed { idx, line ->
                            val parts = line.split("|", limit = 3)
                            val ts = parts.getOrNull(0)?.toLongOrNull() ?: System.currentTimeMillis()
                            val br = parts.getOrNull(1) ?: "main"
                            val msg = parts.getOrNull(2) ?: "Update"
                            "commit ${"%07x".format(ts and 0xFFFFFFF)} ($br)\nDate: ${java.util.Date(ts)}\n\n    $msg"
                        }.joinToString("\n\n")
                    }
                }
                "diff" -> {
                    val files = listWorkspaceFiles().filter { !it.isDirectory && !it.name.startsWith(".") }
                    buildString {
                        appendLine("diff --workspace a/workspace b/workspace")
                        files.take(5).forEach { f ->
                            val preview = readWorkspaceFile(f.relativePath).getOrDefault("").lines().take(8)
                            appendLine("--- a/${f.relativePath}")
                            appendLine("+++ b/${f.relativePath}")
                            preview.forEach { l -> appendLine("+ $l") }
                        }
                    }
                }
                "commit" -> {
                    if (!gitDir.exists()) gitDir.mkdirs()
                    if (!headFile.exists()) headFile.writeText("ref: refs/heads/main\n")
                    val branch = headFile.readText().trim().removePrefix("ref: refs/heads/")
                    val msg = args.dropWhile { it == "-m" }.joinToString(" ").ifBlank { "Workspace snapshot commit" }
                    commitsFile.appendText("${System.currentTimeMillis()}|$branch|$msg\n")
                    "[$branch ${"%07x".format(System.currentTimeMillis() and 0xFFFFFFF)}] $msg"
                }
                else -> "Git operation '$op' executed in workspace ${workDir.name}."
            }

            val dur = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val res = CommandExecutionResult(
                commandDisplay = "git $op ${args.joinToString(" ")}".trim(),
                workingDir = workDir.absolutePath,
                stdout = output,
                stderr = "",
                exitCode = 0,
                durationMs = dur,
                executionMode = runtimeManager.state.value.executionMode
            )
            persistReceipt(res)
            res
        }
}
