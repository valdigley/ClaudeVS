package com.valdigley.claudevs.service

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.valdigley.claudevs.data.model.SSHConnection
import com.valdigley.claudevs.data.model.ProjectTemplate
import com.valdigley.claudevs.data.model.ProjectTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties

data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val duration: Long = 0
)

object AnsiCleaner {
    // Regex to match ANSI escape sequences (cursor, colors, etc.)
    private val ansiRegex = Regex("""\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])""")
    // Additional patterns for cursor visibility and other terminal codes
    private val cursorRegex = Regex("""\[\?25[hl]""")
    // OSC sequences (Operating System Commands) - \x1B] ... \x07 or \x1B\\
    private val oscRegex = Regex("""\x1B\].*?(?:\x07|\x1B\\)""")
    // CSI sequences without ESC prefix (sometimes output separately)
    private val csiNoPrefixRegex = Regex("""\[\d*[A-Za-z]""")
    // Bracketed sequences like [?2004h, [?2004l (bracketed paste mode)
    private val bracketedRegex = Regex("""\[\?\d+[hl]""")
    // Carriage return cleanup
    private val crlfRegex = Regex("""\r\n?""")
    // Multiple newlines cleanup
    private val multiNewlineRegex = Regex("""\n{3,}""")
    // Bell character
    private val bellRegex = Regex("""\x07""")

    fun clean(text: String): String {
        return text
            .replace(ansiRegex, "")
            .replace(oscRegex, "")
            .replace(cursorRegex, "")
            .replace(bracketedRegex, "")
            .replace(csiNoPrefixRegex, "")
            .replace(bellRegex, "")
            .replace(crlfRegex, "\n")
            .replace(multiNewlineRegex, "\n\n")
            .trim()
    }
}

data class ConversationMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Cursor context format support
data class CursorTab(
    val id: String,
    val title: String,
    val messages: List<ConversationMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

data class CursorContext(
    val tabs: List<CursorTab>,
    val activeTabId: String?
)

class ConversationManager {
    private val messages = mutableListOf<ConversationMessage>()
    private val maxMessages = 10 // Keep last N exchanges before summarizing
    private val maxContextChars = 15000 // Max chars before triggering summary
    private var persistedContext: String? = null // Context loaded from file
    private var conversationSummary: String? = null // AI-generated summary of older messages
    private var needsSummary = false // Flag to indicate context needs summarization
    private var currentProjectPath: String? = null // Track which project the context belongs to

    fun addUserMessage(content: String) {
        messages.add(ConversationMessage("user", content))
        checkIfNeedsSummary()
    }

    fun addAssistantMessage(content: String) {
        messages.add(ConversationMessage("assistant", content))
        checkIfNeedsSummary()
    }

    private fun checkIfNeedsSummary() {
        // Check if we need to summarize based on message count or total characters
        val totalChars = messages.sumOf { it.content.length }
        needsSummary = messages.size > maxMessages * 2 || totalChars > maxContextChars
    }

    fun needsContextSummary(): Boolean = needsSummary

    fun getContextForSummary(): String? {
        if (!needsSummary || messages.size < 4) return null // Need at least 2 exchanges

        // Get older messages to summarize (keep recent ones)
        val recentCount = 4 // Keep last 2 exchanges
        val toSummarize = messages.take(messages.size - recentCount)
        if (toSummarize.isEmpty()) return null

        val sb = StringBuilder()
        sb.appendLine("Por favor, resuma a seguinte conversa de forma concisa, mantendo os pontos importantes:")
        sb.appendLine()
        for (msg in toSummarize) {
            val prefix = if (msg.role == "user") "Usuário" else "Claude"
            sb.appendLine("$prefix: ${msg.content}")
        }
        sb.appendLine()
        sb.appendLine("Forneça um resumo em 2-3 parágrafos com os pontos principais discutidos e decisões tomadas.")

        return sb.toString()
    }

    fun applySummary(summary: String) {
        if (summary.isBlank()) return

        // Keep only recent messages
        val recentCount = 4
        val recentMessages = if (messages.size > recentCount) {
            messages.takeLast(recentCount).toMutableList()
        } else {
            messages.toMutableList()
        }

        // Clear and restore
        messages.clear()
        messages.addAll(recentMessages)

        // Combine with existing summary if any
        conversationSummary = if (conversationSummary.isNullOrBlank()) {
            summary
        } else {
            "$conversationSummary\n\n--- Continuação ---\n$summary"
        }

        needsSummary = false
        com.valdigley.claudevs.util.CrashLogger.log("ConversationManager", "Summary applied. Messages: ${messages.size}, Summary chars: ${conversationSummary?.length}")
    }

    fun setPersistedContext(context: String?) {
        persistedContext = context
    }

    private var currentWorkingDir: String? = null

    fun setWorkingDirectory(dir: String?) {
        currentWorkingDir = dir
    }

    // Check if project changed and clear context if needed
    fun setProjectPath(path: String?): Boolean {
        if (path != currentProjectPath) {
            val previousPath = currentProjectPath
            currentProjectPath = path
            if (previousPath != null && path != null) {
                com.valdigley.claudevs.util.CrashLogger.log("ConversationManager", "Project changed from $previousPath to $path - clearing context")
                clearAll()
                return true // Context was cleared
            }
        }
        return false
    }

    fun getCurrentProjectPath(): String? = currentProjectPath

    fun buildContextPrompt(newPrompt: String): String {
        val contextBuilder = StringBuilder()

        // ALWAYS include working directory at the start
        if (!currentWorkingDir.isNullOrBlank()) {
            contextBuilder.appendLine("IMPORTANTE: Diretório de trabalho atual: $currentWorkingDir")
            contextBuilder.appendLine("Todos os arquivos devem ser criados/editados neste diretório ou em subpastas dele.")
            contextBuilder.appendLine()
        }

        // Include persisted context from file if available
        if (!persistedContext.isNullOrBlank()) {
            contextBuilder.appendLine("=== Contexto do projeto (arquivo .claude_context) ===")
            contextBuilder.appendLine(persistedContext)
            contextBuilder.appendLine("=== Fim do contexto do projeto ===")
            contextBuilder.appendLine()
        }

        // Include conversation summary if available (from older messages)
        if (!conversationSummary.isNullOrBlank()) {
            contextBuilder.appendLine("=== Resumo da conversa anterior ===")
            contextBuilder.appendLine(conversationSummary)
            contextBuilder.appendLine("=== Fim do resumo ===")
            contextBuilder.appendLine()
        }

        // Include recent conversation history
        if (messages.isNotEmpty()) {
            contextBuilder.appendLine("=== Conversa atual ===")
            for (msg in messages) {
                val prefix = if (msg.role == "user") "Usuário" else "Claude"
                contextBuilder.appendLine("$prefix: ${msg.content}")
            }
            contextBuilder.appendLine("=== Fim da conversa atual ===")
            contextBuilder.appendLine()
        }

        contextBuilder.appendLine("Usuário: $newPrompt")

        if (messages.isNotEmpty() || !persistedContext.isNullOrBlank() || !conversationSummary.isNullOrBlank()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("(Continue considerando o contexto acima. Responda diretamente ao último pedido.)")
        }

        return contextBuilder.toString()
    }

    // Export conversation to string for saving to file
    fun exportToString(): String {
        val sb = StringBuilder()
        sb.appendLine("# Contexto do Projeto")
        sb.appendLine("# Última atualização: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        sb.appendLine()

        // Include summary if exists
        if (!conversationSummary.isNullOrBlank()) {
            sb.appendLine("## Resumo")
            sb.appendLine(conversationSummary)
            sb.appendLine()
        }

        for (msg in messages) {
            val prefix = if (msg.role == "user") "## Usuário" else "## Claude"
            sb.appendLine(prefix)
            sb.appendLine(msg.content)
            sb.appendLine()
        }
        return sb.toString()
    }

    // Export conversation to Cursor JSON format
    fun exportToCursorJson(): String {
        val now = System.currentTimeMillis()
        val tabId = java.util.UUID.randomUUID().toString()
        val projectName = currentProjectPath?.split("/")?.lastOrNull() ?: "Conversa"

        val messagesJson = messages.map { msg ->
            """{"role":"${msg.role}","content":${org.json.JSONObject.quote(msg.content)},"timestamp":${msg.timestamp}}"""
        }.joinToString(",")

        return """{
  "tabs": [{
    "id": "$tabId",
    "title": "$projectName",
    "createdAt": ${messages.firstOrNull()?.timestamp ?: now},
    "updatedAt": $now,
    "messages": [$messagesJson]
  }],
  "activeTabId": "$tabId"
}"""
    }

    fun clear() {
        messages.clear()
        conversationSummary = null
        needsSummary = false
        // Don't clear persisted context - it's from the file
    }

    fun clearAll() {
        messages.clear()
        persistedContext = null
        conversationSummary = null
        needsSummary = false
    }

    fun hasContext(): Boolean = messages.isNotEmpty() || !persistedContext.isNullOrBlank() || !conversationSummary.isNullOrBlank()

    fun getMessageCount(): Int = messages.size

    // Import messages from Cursor context format
    fun importFromCursor(cursorContext: CursorContext): Int {
        var importedCount = 0
        // Find active tab or use the most recent one
        val activeTab = cursorContext.tabs.find { it.id == cursorContext.activeTabId }
            ?: cursorContext.tabs.maxByOrNull { it.updatedAt }
            ?: return 0

        com.valdigley.claudevs.util.CrashLogger.log("ConversationManager", "Importing from Cursor tab: ${activeTab.title}")

        for (msg in activeTab.messages) {
            when (msg.role) {
                "user" -> {
                    messages.add(msg)
                    importedCount++
                }
                "assistant" -> {
                    messages.add(msg)
                    importedCount++
                }
            }
        }

        checkIfNeedsSummary()
        com.valdigley.claudevs.util.CrashLogger.log("ConversationManager", "Imported $importedCount messages from Cursor")
        return importedCount
    }

    // Parse Cursor JSON format
    companion object {
        fun parseCursorJson(json: String): CursorContext? {
            return try {
                val jsonObject = org.json.JSONObject(json)
                val tabsArray = jsonObject.optJSONArray("tabs") ?: return null
                val tabs = mutableListOf<CursorTab>()

                for (i in 0 until tabsArray.length()) {
                    val tabObj = tabsArray.getJSONObject(i)
                    val messagesArray = tabObj.optJSONArray("messages") ?: continue
                    val messages = mutableListOf<ConversationMessage>()

                    for (j in 0 until messagesArray.length()) {
                        val msgObj = messagesArray.getJSONObject(j)
                        val role = msgObj.optString("role", "user")
                        val content = msgObj.optString("content", "")
                        val timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                        if (content.isNotBlank()) {
                            messages.add(ConversationMessage(role, content, timestamp))
                        }
                    }

                    if (messages.isNotEmpty()) {
                        tabs.add(CursorTab(
                            id = tabObj.optString("id", ""),
                            title = tabObj.optString("title", "Conversa"),
                            messages = messages,
                            createdAt = tabObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = tabObj.optLong("updatedAt", System.currentTimeMillis())
                        ))
                    }
                }

                CursorContext(
                    tabs = tabs,
                    activeTabId = if (jsonObject.has("activeTabId")) jsonObject.getString("activeTabId") else null
                )
            } catch (e: Exception) {
                com.valdigley.claudevs.util.CrashLogger.log("ConversationManager", "Failed to parse Cursor JSON: ${e.message}")
                null
            }
        }
    }

    fun hasPersistedContext(): Boolean = !persistedContext.isNullOrBlank()

    fun hasSummary(): Boolean = !conversationSummary.isNullOrBlank()

    // Get messages as pairs (role, content) for display
    fun getMessagesForDisplay(): List<Pair<String, String>> {
        return messages.map { Pair(it.role, it.content) }
    }

    // Get the current summary
    fun getSummary(): String? = conversationSummary

    // Get older messages for local summary (before truncation)
    fun getOldMessagesForSummary(): List<Pair<String, String>> {
        val recentCount = 4 // Keep last 2 exchanges
        if (messages.size <= recentCount) return emptyList()
        return messages.take(messages.size - recentCount).map { Pair(it.role, it.content) }
    }

    // Apply local truncation (instant, no Claude call)
    fun applyLocalTruncation(localSummary: String) {
        val recentCount = 4
        val recentMessages = if (messages.size > recentCount) {
            messages.takeLast(recentCount).toMutableList()
        } else {
            messages.toMutableList()
        }

        messages.clear()
        messages.addAll(recentMessages)

        // Combine with existing summary
        conversationSummary = if (conversationSummary.isNullOrBlank()) {
            localSummary
        } else {
            "$conversationSummary\n\n$localSummary"
        }

        needsSummary = false
        com.valdigley.claudevs.util.CrashLogger.log("ConversationManager", "Local truncation applied. Messages: ${messages.size}")
    }

    fun getContextStats(): ContextStats {
        val totalChars = messages.sumOf { it.content.length } + (conversationSummary?.length ?: 0)
        return ContextStats(
            messageCount = messages.size,
            totalChars = totalChars,
            hasSummary = !conversationSummary.isNullOrBlank(),
            needsSummary = needsSummary
        )
    }
}

data class ContextStats(
    val messageCount: Int,
    val totalChars: Int,
    val hasSummary: Boolean,
    val needsSummary: Boolean
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val permissions: String = ""
)

class SSHService {
    private var session: Session? = null
    private var currentConnection: SSHConnection? = null
    private var workingDirectory: String? = null
    private var claudePath: String? = null
    private val jsch = JSch()
    private val conversationManager = ConversationManager()

    // Project template detection
    private var detectedTemplate: ProjectTemplate? = null

    // Execution control
    @Volatile private var currentChannel: ChannelExec? = null
    @Volatile private var executionCancelled = false
    @Volatile private var executionTimedOut = false

    suspend fun connect(connection: SSHConnection): Boolean = withContext(Dispatchers.IO) {
        com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Connecting to ${connection.host}:${connection.port} as ${connection.username}")
        try {
            disconnect()
            session = jsch.getSession(connection.username, connection.host, connection.port)
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Session created")

            if (!connection.privateKey.isNullOrBlank()) {
                jsch.addIdentity("key", connection.privateKey.toByteArray(), null, null)
                com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Using private key")
            } else {
                session?.setPassword(connection.password)
                com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Using password")
            }

            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session?.setConfig(config)

            com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Calling session.connect()...")
            session?.connect(30000)
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Session connected: ${session?.isConnected}")

            currentConnection = connection
            workingDirectory = connection.workingDirectory
            val connected = session?.isConnected == true
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "Final result: $connected")
            connected
        } catch (e: Exception) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.connect", "ERROR: ${e.message}\n${e.stackTraceToString()}")
            false
        }
    }

    fun disconnect() {
        try {
            session?.disconnect()
            session = null
            // Keep currentConnection for reconnect
            claudePath = null
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun fullDisconnect() {
        disconnect()
        currentConnection = null
        conversationManager.clear() // Clear conversation on full disconnect
    }

    suspend fun reconnect(): Boolean {
        val connection = currentConnection ?: return false
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Reconnecting to ${connection.name}...")
        return connect(connection)
    }

    fun getCurrentConnection(): SSHConnection? = currentConnection

    suspend fun execute(command: String, useWorkingDir: Boolean = true, timeout: Long = 30000): ExecutionResult = withContext(Dispatchers.IO) {
        com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Starting command: ${command.take(50)}, timeout=${timeout}ms")

        val currentSession = session
        if (currentSession == null) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Session is null")
            return@withContext ExecutionResult(false, "", "Sessão nula")
        }
        if (!currentSession.isConnected) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Session not connected")
            return@withContext ExecutionResult(false, "", "Não conectado")
        }

        val startTime = System.currentTimeMillis()
        var channel: ChannelExec? = null

        try {
            channel = currentSession.openChannel("exec") as ChannelExec
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Channel opened")

            val fullCommand = if (useWorkingDir && !workingDirectory.isNullOrBlank()) {
                "cd \"$workingDirectory\" && $command"
            } else {
                command
            }
            channel.setCommand(fullCommand)
            channel.setInputStream(null)

            // IMPORTANTE: Obter streams ANTES de connect()
            val inputStream = channel.inputStream
            val errorStream = channel.errStream

            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Connecting channel...")
            channel.connect(timeout.toInt())
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Channel connected")

            val outputBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                outputBuilder.appendLine(line)
            }
            reader.close()

            val errorBuilder = StringBuilder()
            val errorReader = BufferedReader(InputStreamReader(errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                errorBuilder.appendLine(line)
            }
            errorReader.close()

            // Esperar o canal fechar para obter exit status (com timeout)
            val waitStart = System.currentTimeMillis()
            while (!channel.isClosed) {
                if (System.currentTimeMillis() - waitStart > timeout) {
                    com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Timeout waiting for channel to close")
                    break
                }
                Thread.sleep(100)
            }

            val duration = System.currentTimeMillis() - startTime
            val exitStatus = channel.exitStatus
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "Finished. exitStatus=$exitStatus, outputLen=${outputBuilder.length}")

            ExecutionResult(
                success = exitStatus == 0,
                output = outputBuilder.toString().trim(),
                error = if (errorBuilder.length > 0) errorBuilder.toString().trim() else null,
                duration = duration
            )
        } catch (e: Exception) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService.execute", "ERROR: ${e.message}\n${e.stackTraceToString()}")
            ExecutionResult(false, "", e.message ?: "Erro desconhecido", System.currentTimeMillis() - startTime)
        } finally {
            try { channel?.disconnect() } catch (e: Exception) { /* ignore */ }
        }
    }

    suspend fun executeClaudeCode(
        prompt: String,
        workingDir: String? = null,
        timeout: Long = 300000,
        useContext: Boolean = true,
        autopilot: Boolean = false,
        developerProfileContext: String? = null,  // Developer profile context to include in prompt
        onStreamLine: ((String) -> Unit)? = null  // Callback for streaming output
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val path = claudePath ?: return@withContext ExecutionResult(false, "", "Claude não encontrado")
        val currentSession = session ?: return@withContext ExecutionResult(false, "", "Sessão nula")

        // Set working directory in conversation manager for context
        conversationManager.setWorkingDirectory(workingDir)

        // Build prompt with conversation context if enabled
        // Include template context + developer profile context at the beginning if available
        val templateContext = getTemplateContext()
        val contextParts = mutableListOf<String>()
        if (templateContext.isNotBlank()) contextParts.add(templateContext)
        if (!developerProfileContext.isNullOrBlank()) contextParts.add(developerProfileContext)
        contextParts.add(prompt)
        val promptWithContext = contextParts.joinToString("\n\n")
        val fullPrompt = if (useContext) conversationManager.buildContextPrompt(promptWithContext) else promptWithContext
        val escapedPrompt = fullPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$").replace("`", "\\`")

        // Build Claude command based on mode
        // Claude Code CLI uses OAuth authentication (Pro/Max plan)
        // -p (--print): Run in non-interactive/headless mode
        // --allowedTools: Pre-approve specific tools

        val claudeCmd = if (autopilot) {
            // Autopilot mode: use --allowedTools to pre-approve all tools
            "\"$path\" -p \"$escapedPrompt\" --allowedTools 'Bash' 'Read' 'Edit' 'Write' 'MultiEdit' 2>&1"
        } else {
            // Non-autopilot mode: use -p for safe read-only output
            "\"$path\" -p \"$escapedPrompt\" 2>&1"
        }

        // CD to workingDir and run Claude directly as the connected user
        val fullCommand = if (!workingDir.isNullOrBlank() && workingDir != "~") {
            "cd \"$workingDir\" && $claudeCmd"
        } else {
            claudeCmd
        }

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Full command: ${fullCommand.take(200)}")

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude command: autopilot=$autopilot, timeout=${timeout}ms")

        // Reset cancellation flags
        executionCancelled = false
        executionTimedOut = false

        val startTime = System.currentTimeMillis()
        var channel: com.jcraft.jsch.ChannelExec? = null

        // Hard timeout: auto-cancel after 3 minutes to prevent hanging forever
        val hardTimeoutMs = 180000L // 3 minutes
        var timeoutJob: Job? = null

        try {
            // Start timeout watcher
            timeoutJob = CoroutineScope(Dispatchers.IO).launch {
                delay(hardTimeoutMs)
                if (!executionCancelled && currentChannel != null) {
                    com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Hard timeout reached (${hardTimeoutMs/1000}s) - cancelling execution")
                    executionTimedOut = true
                    cancelExecution()
                }
            }

            channel = currentSession.openChannel("exec") as com.jcraft.jsch.ChannelExec
            currentChannel = channel // Track for cancellation
            // Always enable PTY for proper terminal emulation
            channel.setPty(true)
            channel.setCommand(fullCommand)
            channel.setInputStream(null)

            val inputStream = channel.inputStream
            val errorStream = channel.errStream

            channel.connect(timeout.toInt())

            val outputBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = null
            while (!executionCancelled) {
                line = reader.readLine() ?: break
                val cleanedLine = AnsiCleaner.clean(line)
                outputBuilder.appendLine(line)
                // Stream each line as it arrives
                if (cleanedLine.isNotBlank() && onStreamLine != null) {
                    withContext(Dispatchers.Main) {
                        onStreamLine(cleanedLine)
                    }
                }
            }
            reader.close()

            // Check if cancelled (by user or timeout)
            if (executionCancelled) {
                val reason = if (executionTimedOut) {
                    "Timeout: execução cancelada após ${hardTimeoutMs/1000} segundos"
                } else {
                    "Execução cancelada pelo usuário"
                }
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Execution was cancelled: $reason")
                return@withContext ExecutionResult(false, AnsiCleaner.clean(outputBuilder.toString()), reason, System.currentTimeMillis() - startTime)
            }

            val errorBuilder = StringBuilder()
            val errorReader = BufferedReader(InputStreamReader(errorStream))
            var errorLine: String?
            while (errorReader.readLine().also { errorLine = it } != null) {
                errorBuilder.appendLine(errorLine)
            }
            errorReader.close()

            // Wait for channel to close with timeout
            val waitStart = System.currentTimeMillis()
            while (!channel.isClosed) {
                if (System.currentTimeMillis() - waitStart > timeout) {
                    com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude timeout waiting for channel close")
                    break
                }
                Thread.sleep(100)
            }

            val duration = System.currentTimeMillis() - startTime
            val exitStatus = channel.exitStatus
            // Clean ANSI escape codes from output
            val rawOutput = outputBuilder.toString()
            val output = AnsiCleaner.clean(rawOutput)

            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude PTY result: exitStatus=$exitStatus, rawLen=${rawOutput.length}, cleanLen=${output.length}")
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude output: ${output.take(200)}")

            // Check for specific error patterns that indicate failure
            val hasErrorPattern = output.contains("Please wait and try again") ||
                output.contains("rate limit") ||
                output.contains("Error:") ||
                output.contains("error:") ||
                output.contains("command not found") ||
                output.startsWith("4") && output.contains("|") // Line numbers from error output

            // Success only if exit status is 0 AND no error patterns detected
            val success = exitStatus == 0 && !hasErrorPattern

            // Save to conversation history only if truly successful
            if (success && useContext && output.isNotBlank()) {
                conversationManager.addUserMessage(prompt) // Original prompt, not with context
                conversationManager.addAssistantMessage(output)
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Saved to conversation. Total messages: ${conversationManager.getMessageCount()}")

                // Auto-save conversation to file after each successful interaction
                if (!workingDir.isNullOrBlank()) {
                    try {
                        // Use chunked base64 save for reliability with large contexts
                        saveContextFile(workingDir)
                    } catch (e: Exception) {
                        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Auto-save failed: ${e.message}")
                    }
                }
            }

            // If failed, try to extract a meaningful error message
            val errorMsg = when {
                hasErrorPattern && output.contains("rate limit") -> "Rate limit atingido. Aguarde um momento."
                hasErrorPattern && output.contains("Please wait") -> "Claude está ocupado. Tente novamente em alguns segundos."
                exitStatus != 0 && output.isNotBlank() -> output // Show Claude's actual output as the error
                exitStatus != 0 && errorBuilder.isNotEmpty() -> AnsiCleaner.clean(errorBuilder.toString())
                exitStatus != 0 -> "Comando falhou (exit code: $exitStatus)"
                else -> null
            }

            ExecutionResult(
                success = success,
                output = output, // Always include output so user can see what happened
                error = if (!success) errorMsg else null,
                duration = duration
            )
        } catch (e: Exception) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude PTY ERROR: ${e.message}")
            ExecutionResult(false, "", e.message ?: "Erro desconhecido", System.currentTimeMillis() - startTime)
        } finally {
            // Cancel the timeout watcher
            timeoutJob?.cancel()
            currentChannel = null // Clear channel reference
            try { channel?.disconnect() } catch (e: Exception) { /* ignore */ }
        }
    }

    suspend fun listDirectory(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "listDirectory called: $path")
        try {
            // Handle ~ specially, use quotes for other paths
            val cmd = if (path == "~" || path.startsWith("~/")) {
                "ls -la $path"
            } else {
                val safePath = path.replace("\"", "\\\"")
                "ls -la \"$safePath\""
            }
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Running: $cmd")
            val result = execute(cmd, useWorkingDir = false)
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Result: success=${result.success}, outputLen=${result.output.length}")

            if (!result.success) {
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "listDirectory failed: ${result.error}")
                return@withContext emptyList()
            }

            val files = mutableListOf<FileItem>()
            val lines = result.output.split("\n").filter { it.isNotBlank() }

            for (line in lines) {
                try {
                    if (line.startsWith("total") || line.endsWith(" .") || line.endsWith(" ..")) continue
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 9) {
                        val permissions = parts[0]
                        val size = parts[4].toLongOrNull() ?: 0
                        val name = parts.drop(8).joinToString(" ")
                        if (name.isNotBlank() && name != "." && name != "..") {
                            files.add(FileItem(name, "$path/$name".replace("//", "/"), permissions.startsWith("d"), size, permissions))
                        }
                    }
                } catch (e: Exception) {
                    com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Parse error for line: $line - ${e.message}")
                }
            }
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Parsed ${files.size} files")
            files.toList() // Return immutable copy
        } catch (e: Exception) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "listDirectory exception: ${e.message}\n${e.stackTraceToString()}")
            emptyList()
        }
    }

    suspend fun createDirectory(path: String) = execute("mkdir -p \"$path\"", useWorkingDir = false)
    suspend fun createFile(path: String, content: String = "") = execute("echo \"${content.replace("\"", "\\\"")}\" > \"$path\"", useWorkingDir = false)
    suspend fun delete(path: String, isDirectory: Boolean = false) = execute(if (isDirectory) "rm -rf \"$path\"" else "rm \"$path\"", useWorkingDir = false)
    suspend fun discoverClaudePath(): String? {
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Discovering Claude path...")
        // Priority: npm global > local bin > Cursor extension
        val cmd = """
            which claude 2>/dev/null ||
            (test -f /usr/local/bin/claude && echo /usr/local/bin/claude) ||
            (test -f ~/.local/bin/claude && echo ~/.local/bin/claude) ||
            (test -f /root/.npm-global/bin/claude && echo /root/.npm-global/bin/claude) ||
            find ~/.cursor-server/extensions -name 'claude' -path '*/native-binary/*' 2>/dev/null | head -1
        """.trimIndent().replace("\n", " ")
        val result = execute(cmd, useWorkingDir = false)
        claudePath = if (result.success && result.output.isNotBlank()) result.output.trim().split("\n").first() else null
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude path discovered: $claudePath")
        return claudePath
    }

    fun hasClaudeCode(): Boolean = claudePath != null
    fun getClaudePath(): String? = claudePath

    // Conversation context management
    fun clearConversation() {
        conversationManager.clear()
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Conversation cleared")
    }
    fun hasConversationContext(): Boolean = conversationManager.hasContext()
    fun getConversationMessageCount(): Int = conversationManager.getMessageCount()
    fun hasPersistedContext(): Boolean = conversationManager.hasPersistedContext()
    fun hasSummary(): Boolean = conversationManager.hasSummary()
    fun needsContextSummary(): Boolean = conversationManager.needsContextSummary()
    fun getContextStats(): ContextStats = conversationManager.getContextStats()

    // Get conversation history for display
    fun getConversationHistory(): List<Pair<String, String>> = conversationManager.getMessagesForDisplay()
    fun getConversationSummary(): String? = conversationManager.getSummary()

    // Truncate context locally (instant, no Claude call needed)
    // Keeps only recent messages and creates a text summary of older ones
    fun summarizeContextIfNeeded(): Boolean {
        if (!conversationManager.needsContextSummary()) return false

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Truncating context locally...")

        // Create a simple local summary of older messages
        val oldMessages = conversationManager.getOldMessagesForSummary()
        if (oldMessages.isEmpty()) return false

        val localSummary = buildString {
            appendLine("=== Conversa anterior (resumida) ===")
            for (msg in oldMessages.takeLast(6)) { // Keep summaries of last 3 exchanges
                val role = if (msg.first == "user") "Usuário" else "Claude"
                val preview = msg.second.take(200).replace("\n", " ")
                appendLine("$role: $preview${if (msg.second.length > 200) "..." else ""}")
            }
        }

        conversationManager.applyLocalTruncation(localSummary)
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Context truncated successfully")
        return true
    }

    fun applySummary(summary: String) {
        conversationManager.applySummary(summary)
    }

    // Load context from .claude_context file in working directory
    // Also supports Cursor format (.cursor_context.json)
    suspend fun loadContextFile(path: String): Boolean {
        // Check if project changed - save current context before switching
        val previousPath = conversationManager.getCurrentProjectPath()
        if (previousPath != null && previousPath != path && conversationManager.getMessageCount() > 0) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Saving context for $previousPath before switching to $path")
            saveContextFile(previousPath)
        }

        // Now clear and set new project path
        conversationManager.setProjectPath(path)

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Loading context for project: $path")

        // First try Cursor format (.cursor_context.json)
        val cursorResult = loadCursorContextFile(path)
        if (cursorResult) {
            return true
        }

        // Fall back to ClaudeVS format (.claude_context)
        val contextPath = "$path/.claude_context"
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Loading context from: $contextPath")
        val result = execute("cat \"$contextPath\" 2>/dev/null", useWorkingDir = false)
        if (result.success && result.output.isNotBlank()) {
            val content = result.output

            // Try to parse individual messages from the saved format
            // Format: ## Usuário\n<content>\n\n## Claude\n<content>\n\n
            var parsedMessages = 0
            val sections = content.split(Regex("(?=## Usuário|## Claude)"))
            for (section in sections) {
                val trimmed = section.trim()
                when {
                    trimmed.startsWith("## Usuário") -> {
                        val msg = trimmed.removePrefix("## Usuário").trim()
                        if (msg.isNotBlank()) {
                            conversationManager.addUserMessage(msg)
                            parsedMessages++
                        }
                    }
                    trimmed.startsWith("## Claude") -> {
                        val msg = trimmed.removePrefix("## Claude").trim()
                        if (msg.isNotBlank()) {
                            conversationManager.addAssistantMessage(msg)
                            parsedMessages++
                        }
                    }
                }
            }

            // Only set persisted context if we couldn't parse messages (fallback for old format)
            if (parsedMessages == 0) {
                conversationManager.setPersistedContext(content)
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Context loaded as raw text: ${content.length} chars")
            } else {
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Context loaded: parsed $parsedMessages messages from conversation history")
            }
            return true
        }
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "No context file found")
        return false
    }

    // Load Cursor format context file
    private suspend fun loadCursorContextFile(path: String): Boolean {
        val cursorPath = "$path/.cursor_context.json"
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Checking for Cursor context: $cursorPath")
        val result = execute("cat \"$cursorPath\" 2>/dev/null", useWorkingDir = false)
        if (result.success && result.output.isNotBlank()) {
            val cursorContext = ConversationManager.parseCursorJson(result.output)
            if (cursorContext != null && cursorContext.tabs.isNotEmpty()) {
                val importedCount = conversationManager.importFromCursor(cursorContext)
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Cursor context imported: $importedCount messages")
                return importedCount > 0
            }
        }
        return false
    }

    // Save context to .claude_context file in working directory
    suspend fun saveContextFile(path: String): Boolean {
        val contextPath = "$path/.claude_context"
        val content = conversationManager.exportToString()
        if (content.isBlank()) return false

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Saving context to: $contextPath (${content.length} chars)")

        // Use base64 encoding to handle large content and special characters safely
        val base64Content = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

        // For very large content, split into chunks
        val chunkSize = 50000 // ~50KB chunks
        val result = if (base64Content.length > chunkSize) {
            // Chunked write for large content
            saveContextChunked(contextPath, base64Content, chunkSize)
        } else {
            // Single write for smaller content
            val cmd = "echo '$base64Content' | base64 -d > \"$contextPath\""
            execute(cmd, useWorkingDir = false, timeout = 30000)
        }

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Context saved: ${result.success}")
        return result.success
    }

    // Save large context in chunks to avoid SSH command size limits
    private suspend fun saveContextChunked(contextPath: String, base64Content: String, chunkSize: Int): ExecutionResult {
        val chunks = base64Content.chunked(chunkSize)
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Saving in ${chunks.size} chunks")

        // Create temp file for base64 content
        val tempPath = "/tmp/.claude_context_temp_${System.currentTimeMillis()}"

        for ((index, chunk) in chunks.withIndex()) {
            val operator = if (index == 0) ">" else ">>"
            val cmd = "echo -n '$chunk' $operator \"$tempPath\""
            val result = execute(cmd, useWorkingDir = false, timeout = 15000)
            if (!result.success) {
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Chunk $index failed: ${result.error}")
                execute("rm -f \"$tempPath\"", useWorkingDir = false)
                return result
            }
        }

        // Decode base64 and move to final location
        val decodeCmd = "base64 -d \"$tempPath\" > \"$contextPath\" && rm -f \"$tempPath\""
        return execute(decodeCmd, useWorkingDir = false, timeout = 30000)
    }

    // Save context in Cursor JSON format (for compatibility)
    suspend fun saveContextFileCursor(path: String): Boolean {
        val cursorPath = "$path/.cursor_context.json"
        val content = conversationManager.exportToCursorJson()
        if (content.isBlank()) return false

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Saving Cursor context to: $cursorPath")

        // Use base64 encoding for safe transfer
        val base64Content = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val cmd = "echo '$base64Content' | base64 -d > \"$cursorPath\""
        val result = execute(cmd, useWorkingDir = false, timeout = 30000)

        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Cursor context saved: ${result.success}")
        return result.success
    }

    // Get Cursor format export for display/copy
    fun getContextAsCursorJson(): String = conversationManager.exportToCursorJson()

    suspend fun installClaudeCode(): ExecutionResult {
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Installing Claude Code...")
        // Install using npm globally, with longer timeout
        val result = execute("npm install -g @anthropic-ai/claude-code 2>&1", useWorkingDir = false, timeout = 300000) // 5 min timeout
        if (result.success) {
            // Re-discover path after install
            discoverClaudePath()
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Claude installed, path: $claudePath")
        }
        return result
    }

    suspend fun loginClaudeCode(): ExecutionResult {
        val path = claudePath ?: return ExecutionResult(false, "", "Claude não encontrado")
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Starting Claude login...")
        // Run claude login and capture output with URL
        return execute("\"$path\" login 2>&1", useWorkingDir = false, timeout = 60000)
    }

    suspend fun checkClaudeAuth(): Boolean {
        val path = claudePath ?: return false
        // Try running a simple command to check if authenticated
        val result = execute("\"$path\" --version 2>&1", useWorkingDir = false, timeout = 10000)
        return result.success
    }
    suspend fun getCurrentDirectory(): String = execute("pwd").let { if (it.success) it.output.trim() else workingDirectory ?: "~" }
    fun isConnected(): Boolean = session?.isConnected == true
    fun getWorkingDirectory(): String? = workingDirectory
    fun setWorkingDirectory(dir: String?) { workingDirectory = dir }

    // Project template detection
    suspend fun detectProjectType(path: String): ProjectTemplate {
        com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Detecting project type for: $path")
        try {
            // List files in directory
            val result = execute("ls -la \"$path\" 2>/dev/null | awk '{print \$NF}'", useWorkingDir = false)
            if (result.success) {
                val fileNames = result.output.split("\n").filter { it.isNotBlank() && it != "." && it != ".." }
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Files found: ${fileNames.take(10)}")

                // Also check package.json for more specific detection
                val packageResult = execute("cat \"$path/package.json\" 2>/dev/null | grep -E '(vite|next|express|supabase)' | head -5", useWorkingDir = false)
                val packageIndicators = if (packageResult.success) packageResult.output.split("\n") else emptyList()

                val allIndicators = fileNames + packageIndicators
                detectedTemplate = ProjectTemplates.detectFromFiles(allIndicators)
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Detected template: ${detectedTemplate?.name}")
            } else {
                detectedTemplate = ProjectTemplates.GENERIC
            }
        } catch (e: Exception) {
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Error detecting project type: ${e.message}")
            detectedTemplate = ProjectTemplates.GENERIC
        }
        return detectedTemplate ?: ProjectTemplates.GENERIC
    }

    fun getDetectedTemplate(): ProjectTemplate? = detectedTemplate
    fun getTemplateContext(): String = detectedTemplate?.context ?: ""
    fun clearDetectedTemplate() { detectedTemplate = null }

    // Cancel current execution
    fun cancelExecution() {
        executionCancelled = true
        currentChannel?.let { channel ->
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Cancelling execution...")
            try {
                // Send SIGINT (Ctrl+C) to the process if PTY is available
                channel.sendSignal("INT")
            } catch (e: Exception) {
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Failed to send signal: ${e.message}")
            }
            try {
                channel.disconnect()
            } catch (e: Exception) {
                com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Failed to disconnect channel: ${e.message}")
            }
            currentChannel = null
            com.valdigley.claudevs.util.CrashLogger.log("SSHService", "Execution cancelled")
        }
    }

    fun isExecuting(): Boolean = currentChannel != null && !executionCancelled
}
