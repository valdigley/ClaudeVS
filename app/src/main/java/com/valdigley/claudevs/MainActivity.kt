package com.valdigley.claudevs

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.valdigley.claudevs.data.database.AppDatabase
import com.valdigley.claudevs.data.model.SSHConnection
import com.valdigley.claudevs.data.model.DeveloperProfile
import com.valdigley.claudevs.data.model.TaskChecklist
import com.valdigley.claudevs.data.model.TaskParser
import com.valdigley.claudevs.data.model.TaskStatus
import com.valdigley.claudevs.data.model.ProjectTemplate
import com.valdigley.claudevs.service.ContextStats
import com.valdigley.claudevs.service.FileItem
import com.valdigley.claudevs.service.SSHService
import com.valdigley.claudevs.service.SSHForegroundService
import com.valdigley.claudevs.ui.screens.*
import com.valdigley.claudevs.ui.theme.Background
import com.valdigley.claudevs.ui.theme.ClaudeVSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.valdigley.claudevs.util.CrashLogger
import com.valdigley.claudevs.util.AppUpdateChecker
import com.valdigley.claudevs.util.AppUpdate

class MainActivity : ComponentActivity() {
    private lateinit var updateChecker: AppUpdateChecker
    private lateinit var database: AppDatabase

    // Service binding
    private var sshForegroundService: SSHForegroundService? = null
    private var serviceBound = mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            CrashLogger.log("MainActivity", "Service connected")
            val binder = service as SSHForegroundService.SSHBinder
            sshForegroundService = binder.getService()
            serviceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            CrashLogger.log("MainActivity", "Service disconnected")
            sshForegroundService = null
            serviceBound.value = false
        }
    }

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        CrashLogger.log("MainActivity", "Notification permission granted: $isGranted")
        // Start service regardless of permission (notification just won't show)
        startAndBindService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(this)
        CrashLogger.log("MainActivity", "onCreate started")
        database = AppDatabase.getDatabase(this)
        updateChecker = AppUpdateChecker(this)

        // Request notification permission on Android 13+ before starting service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    startAndBindService()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startAndBindService()
        }

        setContent {
            ClaudeVSTheme {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    val isBound by serviceBound
                    if (isBound && sshForegroundService != null) {
                        MainApp(database, sshForegroundService!!.sshService, updateChecker, lifecycleScope, sshForegroundService!!) {
                            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Show loading while waiting for service
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    private fun startAndBindService() {
        CrashLogger.log("MainActivity", "Starting and binding service")
        val serviceIntent = Intent(this, SSHForegroundService::class.java)

        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Bind to service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        CrashLogger.log("MainActivity", "onDestroy")
        // Unbind but don't stop service - connection stays alive
        if (serviceBound.value) {
            unbindService(serviceConnection)
            serviceBound.value = false
        }
        super.onDestroy()
    }
}

@Composable
fun MainApp(database: AppDatabase, sshService: SSHService, updateChecker: AppUpdateChecker, stableScope: CoroutineScope, foregroundService: SSHForegroundService, showToast: (String) -> Unit) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    // Use stableScope for long-running operations (like Claude execution) to survive recomposition

    var connections by remember { mutableStateOf<List<SSHConnection>>(emptyList()) }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var currentPath by remember { mutableStateOf("~") }
    var terminalOutput by remember { mutableStateOf<List<TerminalLine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isClaudeMode by remember { mutableStateOf(true) } // Start in Claude mode by default
    var hasClaudeCode by remember { mutableStateOf(false) }
    var isInstallingClaude by remember { mutableStateOf(false) }
    var isReconnecting by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var editingConnection by remember { mutableStateOf<SSHConnection?>(null) }
    var isAutopilot by remember { mutableStateOf(true) } // Start in Autopilot mode by default
    var developerProfile by remember { mutableStateOf<DeveloperProfile?>(null) }
    var currentChecklist by remember { mutableStateOf<TaskChecklist?>(null) }
    var contextStats by remember { mutableStateOf<ContextStats?>(null) }
    var detectedTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }

    // Update checker state
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { database.connectionDao().getAllConnections().collectLatest { connections = it } }
    LaunchedEffect(Unit) { database.developerProfileDao().getProfile().collectLatest { developerProfile = it } }

    // Check for updates on app start
    LaunchedEffect(Unit) {
        val update = updateChecker.checkForUpdate()
        if (update != null) {
            availableUpdate = update
            showUpdateDialog = true
            CrashLogger.log("UpdateChecker", "Update available: ${update.versionName}")
        }
    }

    fun addOutput(text: String, type: LineType = LineType.OUTPUT) {
        try {
            CrashLogger.log("addOutput", "Adding: ${text.take(50)}...")
            val newLine = TerminalLine(text, type)
            val newList = terminalOutput.toMutableList()
            newList.add(newLine)
            terminalOutput = newList.toList()
            CrashLogger.log("addOutput", "Now have ${terminalOutput.size} lines")
        } catch (e: Exception) {
            CrashLogger.log("addOutput", "ERROR: ${e.message}\n${e.stackTraceToString()}")
        }
    }

    suspend fun connectToServer(connection: SSHConnection) {
        // Prevent multiple simultaneous connections
        if (isConnecting) {
            CrashLogger.log("connectToServer", "Already connecting, ignoring")
            return
        }
        isConnecting = true
        isLoading = true
        try {
            if (sshService.connect(connection)) {
                isConnected = true
                // Notify foreground service of connection
                foregroundService.updateConnectionState(true, connection.name)

                // Use connection's workingDirectory if set, otherwise get current directory
                currentPath = if (!connection.workingDirectory.isNullOrBlank()) {
                    // Navigate to the configured working directory
                    sshService.execute("cd \"${connection.workingDirectory}\" && pwd")
                    connection.workingDirectory
                } else {
                    sshService.getCurrentDirectory()
                }
                val claudePath = sshService.discoverClaudePath()
                if (claudePath != null) {
                    hasClaudeCode = true
                    // Auto-load context file from working directory
                    val contextLoaded = sshService.loadContextFile(currentPath)
                    val msgCount = sshService.getConversationMessageCount()
                    if (contextLoaded && msgCount > 0) {
                        addOutput("📄 Histórico carregado ($msgCount mensagens)", LineType.INFO)
                    }
                    // Auto-detect project type
                    val template = sshService.detectProjectType(currentPath)
                    detectedTemplate = template
                    if (template.id != "generic") {
                        addOutput("${template.icon} Projeto detectado: ${template.name}", LineType.INFO)
                    }
                } else {
                    hasClaudeCode = false
                    addOutput("⚠️ Claude Code não encontrado - use o botão para instalar", LineType.ERROR)
                }
                database.connectionDao().updateLastConnected(connection.id, System.currentTimeMillis())
            } else {
                foregroundService.updateConnectionState(false)
                addOutput("❌ Falha na conexão", LineType.ERROR)
            }
        } catch (e: Exception) {
            foregroundService.updateConnectionState(false)
            addOutput("❌ ${e.message}", LineType.ERROR)
        }
        isLoading = false
        isConnecting = false
    }

    suspend fun reconnect(maxRetries: Int = 3): Boolean {
        if (isReconnecting) return false
        isReconnecting = true

        for (attempt in 1..maxRetries) {
            addOutput("\n🔄 Reconectando... (tentativa $attempt/$maxRetries)", LineType.INFO)
            try {
                if (sshService.reconnect()) {
                    isConnected = true
                    currentPath = sshService.getCurrentDirectory()
                    addOutput("✅ Reconectado!", LineType.SUCCESS)
                    // Notify foreground service
                    val conn = sshService.getCurrentConnection()
                    foregroundService.updateConnectionState(true, conn?.name)
                    // Re-discover Claude path after reconnect
                    val claudePath = sshService.discoverClaudePath()
                    hasClaudeCode = claudePath != null
                    if (hasClaudeCode) {
                        addOutput("🤖 Claude Code: $claudePath", LineType.CLAUDE)
                    }
                    isReconnecting = false
                    return true
                }
            } catch (e: Exception) {
                addOutput("❌ Tentativa $attempt falhou: ${e.message}", LineType.ERROR)
            }
            if (attempt < maxRetries) {
                addOutput("⏳ Aguardando 2s...", LineType.INFO)
                kotlinx.coroutines.delay(2000)
            }
        }

        addOutput("❌ Falha após $maxRetries tentativas", LineType.ERROR)
        addOutput("💡 Use o botão para reconectar manualmente", LineType.INFO)
        isConnected = false
        foregroundService.updateConnectionState(false)
        isReconnecting = false
        return false
    }

    suspend fun loginClaude() {
        addOutput("\n🔐 Iniciando login do Claude...", LineType.INFO)
        try {
            val result = sshService.loginClaudeCode()
            if (result.output.isNotBlank()) {
                addOutput("📋 Saída do login:", LineType.INFO)
                // Parse output to find URL
                val urlRegex = Regex("https://[^\\s]+")
                val urls = urlRegex.findAll(result.output).map { it.value }.toList()
                if (urls.isNotEmpty()) {
                    addOutput("🔗 Copie e abra este link no navegador:", LineType.SUCCESS)
                    urls.forEach { url ->
                        addOutput("   $url", LineType.CLAUDE)
                    }
                } else {
                    addOutput(result.output, LineType.OUTPUT)
                }
            }
            if (result.error != null) {
                addOutput(result.error, LineType.ERROR)
            }
        } catch (e: Exception) {
            addOutput("❌ Erro: ${e.message}", LineType.ERROR)
        }
    }

    suspend fun executeCommand(command: String) {
        CrashLogger.log("executeCommand", "Starting: $command")
        CrashLogger.log("executeCommand", "isConnected=$isConnected, sshService.isConnected=${sshService.isConnected()}")

        // Try auto-reconnect if disconnected
        if (!sshService.isConnected()) {
            isConnected = false
            CrashLogger.log("executeCommand", "Not connected, trying auto-reconnect")
            if (sshService.getCurrentConnection() != null) {
                val reconnected = reconnect()
                if (!reconnected) {
                    showToast("Não conectado")
                    return
                }
            } else {
                showToast("Não conectado")
                return
            }
        }

        isLoading = true
        try {
            // Commands starting with "!" execute directly in shell (bypass Claude)
            val isDirectShellCommand = command.startsWith("!")
            val actualCommand = if (isDirectShellCommand) command.substring(1).trim() else command

            addOutput("\n$ $actualCommand", LineType.COMMAND)
            CrashLogger.log("executeCommand", "Calling SSH execute... isDirectShell=$isDirectShellCommand")

            var streamingStarted = false
            val outputLines = mutableListOf<String>()
            val result = if (isClaudeMode && !isDirectShellCommand) {
                CrashLogger.log("executeCommand", "Using Claude mode with autopilot=$isAutopilot")
                // Add Claude header before streaming starts
                addOutput("\n🤖 Claude:", LineType.CLAUDE)
                sshService.executeClaudeCode(
                    prompt = actualCommand,
                    workingDir = currentPath,
                    autopilot = isAutopilot,
                    developerProfileContext = developerProfile?.toContextString(),
                    onStreamLine = { line ->
                        streamingStarted = true
                        outputLines.add(line)
                        addOutput(line, LineType.OUTPUT)

                        // Try to detect task plan in early output
                        if (outputLines.size <= 20 && currentChecklist == null) {
                            val fullText = outputLines.joinToString("\n")
                            if (TaskParser.containsPlan(fullText)) {
                                val checklist = TaskParser.createChecklist(actualCommand.take(50), fullText)
                                if (checklist != null) {
                                    currentChecklist = checklist.withNextStepInProgress()
                                    CrashLogger.log("executeCommand", "Created checklist with ${checklist.steps.size} steps")
                                }
                            }
                        }

                        // Update checklist progress based on completion indicators
                        currentChecklist?.let { checklist ->
                            if (!checklist.isComplete && TaskParser.indicatesCompletion(line)) {
                                currentChecklist = checklist.withCurrentStepCompleted()
                                CrashLogger.log("executeCommand", "Checklist step completed: ${currentChecklist?.completedCount}/${checklist.steps.size}")
                            }
                        }
                    }
                )
            } else {
                // Direct shell execution
                sshService.execute(actualCommand)
            }
            CrashLogger.log("executeCommand", "SSH result: success=${result.success}, output=${result.output.take(100)}")

            // Only show final result if not using streaming or if there was an error
            if (!isClaudeMode || isDirectShellCommand) {
                // Shell command - show full output
                if (result.success) {
                    addOutput(result.output.ifBlank { "(ok)" }, LineType.OUTPUT)
                } else {
                    addOutput("❌ ${result.error ?: result.output}", LineType.ERROR)
                }
            } else if (!result.success) {
                // Claude mode failed - show error
                addOutput("❌ ${result.error ?: result.output}", LineType.ERROR)
            } else if (!streamingStarted && result.output.isNotBlank()) {
                // Claude mode succeeded but no streaming happened - show output
                addOutput(result.output, LineType.OUTPUT)
            }

            if (command.startsWith("cd ")) {
                currentPath = sshService.getCurrentDirectory()
                CrashLogger.log("executeCommand", "Updated path to: $currentPath")
            }

            // Update context stats after execution
            contextStats = sshService.getContextStats()

            // Finalize checklist if complete
            currentChecklist?.let { checklist ->
                if (checklist.isComplete || result.success) {
                    // Mark remaining as complete if task succeeded
                    var finalChecklist = checklist
                    while (!finalChecklist.isComplete) {
                        finalChecklist = finalChecklist.withCurrentStepCompleted()
                    }
                    finalChecklist = finalChecklist.copy(
                        endTime = System.currentTimeMillis(),
                        summary = "Tarefa concluída com ${finalChecklist.completedCount} passos"
                    )
                    currentChecklist = finalChecklist

                    // Show summary in terminal
                    val summary = TaskParser.generateSummary(finalChecklist)
                    addOutput(summary, LineType.SUCCESS)

                    // Clear checklist after showing summary (delayed)
                    kotlinx.coroutines.delay(5000)
                    currentChecklist = null
                }
            }

            // Auto-summarize context if needed
            if (isClaudeMode && sshService.needsContextSummary()) {
                addOutput("\n📝 Resumindo contexto...", LineType.INFO)
                val summarized = sshService.summarizeContextIfNeeded()
                if (summarized) {
                    addOutput("✅ Contexto resumido automaticamente", LineType.SUCCESS)
                    contextStats = sshService.getContextStats()
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("executeCommand", "ERROR: ${e.message}\n${e.stackTraceToString()}")
            addOutput("❌ ${e.message}", LineType.ERROR)
            // Mark checklist as failed
            currentChecklist?.let { checklist ->
                val currentStep = checklist.steps.firstOrNull { it.status == TaskStatus.IN_PROGRESS }
                if (currentStep != null) {
                    currentChecklist = checklist.withStepStatus(currentStep.id, TaskStatus.FAILED)
                }
            }
        } finally {
            isLoading = false
            CrashLogger.log("executeCommand", "Finished")
        }
    }

    suspend fun installClaude() {
        if (isInstallingClaude) return
        isInstallingClaude = true
        addOutput("\n📦 Instalando Claude Code...", LineType.INFO)
        addOutput("⏳ Isso pode levar alguns minutos...", LineType.INFO)
        try {
            val result = sshService.installClaudeCode()
            if (result.success) {
                hasClaudeCode = sshService.hasClaudeCode()
                if (hasClaudeCode) {
                    addOutput("✅ Claude Code instalado!", LineType.SUCCESS)
                    addOutput("   📍 ${sshService.getClaudePath()}", LineType.INFO)
                    // Automatically start login process
                    loginClaude()
                } else {
                    addOutput("⚠️ Instalação concluída mas Claude não encontrado", LineType.ERROR)
                    addOutput(result.output, LineType.OUTPUT)
                }
            } else {
                addOutput("❌ Falha na instalação", LineType.ERROR)
                addOutput(result.error ?: result.output, LineType.ERROR)
            }
        } catch (e: Exception) {
            addOutput("❌ Erro: ${e.message}", LineType.ERROR)
        }
        isInstallingClaude = false
    }

    suspend fun loadFiles(path: String) {
        CrashLogger.log("loadFiles", "Starting load for path: $path")
        if (isLoading) {
            CrashLogger.log("loadFiles", "Already loading, returning")
            return
        }
        isLoading = true
        // Don't clear files here - causes double recomposition crash
        try {
            CrashLogger.log("loadFiles", "Calling listDirectory")
            val loadedFiles = sshService.listDirectory(path)
            CrashLogger.log("loadFiles", "Got ${loadedFiles.size} files")
            // Update state atomically
            currentPath = path
            files = loadedFiles
            CrashLogger.log("loadFiles", "Files set successfully")
        } catch (e: Exception) {
            CrashLogger.log("loadFiles", "ERROR: ${e.message}\n${e.stackTraceToString()}")
            showToast("Erro: ${e.message}")
        } finally {
            isLoading = false
            CrashLogger.log("loadFiles", "Finished")
        }
    }

    // Update Dialog
    if (showUpdateDialog && availableUpdate != null) {
        UpdateDialog(
            update = availableUpdate!!,
            currentVersion = updateChecker.getCurrentVersion(),
            isDownloading = isDownloadingUpdate,
            onDismiss = { if (!isDownloadingUpdate) showUpdateDialog = false },
            onUpdate = {
                isDownloadingUpdate = true
                updateChecker.downloadAndInstall(
                    availableUpdate!!,
                    onProgress = { },
                    onComplete = {
                        isDownloadingUpdate = false
                        showUpdateDialog = false
                        showToast("Download concluído! Instalando...")
                    },
                    onError = { error ->
                        isDownloadingUpdate = false
                        showToast("Erro: $error")
                    }
                )
            }
        )
    }

    NavHost(navController, startDestination = "connections") {
        composable("connections") {
            ConnectionsScreen(connections,
                onConnectionClick = { scope.launch { terminalOutput = emptyList(); connectToServer(it); navController.navigate("terminal/${it.id}") } },
                onFilesClick = { conn -> scope.launch {
                    terminalOutput = emptyList()
                    connectToServer(conn)
                    if (sshService.isConnected()) {
                        loadFiles(currentPath)
                        navController.navigate("files/${conn.id}")
                    } else {
                        showToast("Falha na conexão")
                    }
                } },
                onAddClick = { editingConnection = null; navController.navigate("add") },
                onEditClick = { editingConnection = it; navController.navigate("add") },
                onDeleteClick = { scope.launch { database.connectionDao().deleteConnection(it); showToast("Excluído") } },
                onSetDefault = { scope.launch { database.connectionDao().clearDefaultConnection(); database.connectionDao().setDefaultConnection(it.id); showToast("Padrão definido") } },
                onOpenProfile = { navController.navigate("profile") }
            )
        }
        
        composable("add") {
            AddConnectionScreen(editingConnection,
                onSave = { scope.launch { if (it.id == 0L) database.connectionDao().insertConnection(it) else database.connectionDao().updateConnection(it); showToast("Salvo"); navController.popBackStack() } },
                onBack = { navController.popBackStack() },
                onTest = { scope.launch { showToast("Testando..."); try { if (sshService.connect(it)) { val claude = sshService.discoverClaudePath(); sshService.disconnect(); showToast(if (claude != null) "✅ OK! Claude: $claude" else "✅ OK! Sem Claude") } else showToast("❌ Falha") } catch (e: Exception) { showToast("❌ ${e.message}") } } }
            )
        }
        
        composable("terminal/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val connection = connections.find { it.id == entry.arguments?.getLong("id") }
            connection?.let {
                TerminalScreen(it, isConnected, currentPath, terminalOutput, isLoading, isClaudeMode,
                    hasClaudeCode = hasClaudeCode,
                    isInstallingClaude = isInstallingClaude,
                    isReconnecting = isReconnecting,
                    hasConversationContext = sshService.hasConversationContext(),
                    conversationMessageCount = sshService.getConversationMessageCount(),
                    isAutopilot = isAutopilot,
                    hasPersistedContext = sshService.hasPersistedContext(),
                    contextStats = contextStats,
                    currentChecklist = currentChecklist,
                    detectedTemplate = detectedTemplate,
                    onExecuteCommand = { cmd -> stableScope.launch { executeCommand(cmd) } },
                    onToggleClaudeMode = { isClaudeMode = !isClaudeMode; addOutput(if (isClaudeMode) "\n🤖 Claude Mode ON" else "\n$ Modo terminal", if (isClaudeMode) LineType.CLAUDE else LineType.INFO) },
                    onInstallClaude = { scope.launch { installClaude() } },
                    onReconnect = { scope.launch { reconnect(1) } },
                    onClearConversation = { sshService.clearConversation(); currentChecklist = null; contextStats = null; addOutput("🗑️ Contexto da conversa limpo", LineType.INFO) },
                    onToggleAutopilot = {
                        isAutopilot = !isAutopilot
                        addOutput(if (isAutopilot) "🚀 Autopilot ON - Claude pode executar comandos" else "⏸️ Autopilot OFF - Modo somente leitura", if (isAutopilot) LineType.SUCCESS else LineType.INFO)
                    },
                    onSaveContext = { scope.launch {
                        if (sshService.saveContextFile(currentPath)) {
                            addOutput("💾 Contexto salvo em $currentPath/.claude_context", LineType.SUCCESS)
                        } else {
                            addOutput("❌ Erro ao salvar contexto", LineType.ERROR)
                        }
                    } },
                    onClearScreen = { terminalOutput = emptyList() },
                    onStopExecution = {
                        sshService.cancelExecution()
                        addOutput("⏹️ Execução cancelada", LineType.INFO)
                        isLoading = false
                        currentChecklist = null
                    },
                    onBack = { navController.popBackStack() },
                    onOpenFiles = { scope.launch {
                        try {
                            if (sshService.isConnected()) {
                                loadFiles(currentPath)
                                navController.navigate("files/${it.id}")
                            } else {
                                showToast("Não conectado")
                            }
                        } catch (e: Exception) {
                            showToast("Erro: ${e.message}")
                        }
                    } },
                    onDisconnect = { sshService.fullDisconnect(); isConnected = false; foregroundService.updateConnectionState(false) }
                )
            }
        }
        
        composable("files/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            FilesScreen(currentPath, files, isLoading,
                onNavigateToFolder = { file ->
                    CrashLogger.log("Navigation", "onNavigateToFolder: ${file.path}")
                    scope.launch { loadFiles(file.path) }
                },
                onNavigateUp = { scope.launch {
                    try {
                        val parentPath = when {
                            currentPath == "~" || currentPath == "/" -> currentPath
                            currentPath.startsWith("~/") -> currentPath.substringBeforeLast("/").ifBlank { "~" }
                            else -> currentPath.substringBeforeLast("/").ifBlank { "/" }
                        }
                        loadFiles(parentPath)
                    } catch (e: Exception) { showToast("Erro") }
                } },
                onNavigateHome = { scope.launch { loadFiles("~") } },
                onNavigateToPath = { scope.launch { loadFiles(it) } },
                onRefresh = { scope.launch { loadFiles(currentPath) } },
                onCreateFolder = { scope.launch { val r = sshService.createDirectory("$currentPath/$it"); if (r.success) { showToast("Pasta criada"); loadFiles(currentPath) } else showToast("Erro") } },
                onCreateFile = { scope.launch { val r = sshService.createFile("$currentPath/$it"); if (r.success) { showToast("Arquivo criado"); loadFiles(currentPath) } else showToast("Erro") } },
                onDelete = { scope.launch { val r = sshService.delete(it.path, it.isDirectory); if (r.success) { showToast("Excluído"); loadFiles(currentPath) } else showToast("Erro") } },
                onSetWorkingDir = { showToast("Diretório: $currentPath") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("profile") {
            DeveloperProfileScreen(
                existingProfile = developerProfile,
                onSave = { profile ->
                    scope.launch {
                        database.developerProfileDao().insertOrUpdate(profile)
                        showToast("Perfil salvo")
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun UpdateDialog(
    update: AppUpdate,
    currentVersion: String,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Atualização Disponível") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("Versão ${update.versionName} disponível!")
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    "Versão atual: $currentVersion",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                if (update.releaseNotes.isNotBlank()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text(
                        update.releaseNotes.take(200),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                if (isDownloading) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    androidx.compose.material3.Text("Baixando...", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onUpdate, enabled = !isDownloading) {
                androidx.compose.material3.Text(if (isDownloading) "Baixando..." else "Atualizar")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    androidx.compose.material3.Text("Depois")
                }
            }
        }
    )
}
