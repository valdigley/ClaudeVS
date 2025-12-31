package com.valdigley.claudevs.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.valdigley.claudevs.data.model.SSHConnection
import com.valdigley.claudevs.data.model.QuickAction
import com.valdigley.claudevs.data.model.defaultQuickActions
import com.valdigley.claudevs.data.model.TaskChecklist
import com.valdigley.claudevs.data.model.TaskStatus
import com.valdigley.claudevs.data.model.ProjectTemplate
import com.valdigley.claudevs.service.ContextStats
import com.valdigley.claudevs.ui.theme.*

data class TerminalLine(val text: String, val type: LineType = LineType.OUTPUT)
enum class LineType { COMMAND, OUTPUT, SUCCESS, ERROR, CLAUDE, INFO }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(
    connection: SSHConnection, isConnected: Boolean, currentPath: String, terminalOutput: List<TerminalLine>,
    isLoading: Boolean, isClaudeMode: Boolean, hasClaudeCode: Boolean = true, isInstallingClaude: Boolean = false,
    isReconnecting: Boolean = false, hasConversationContext: Boolean = false, conversationMessageCount: Int = 0,
    isAutopilot: Boolean = false, hasPersistedContext: Boolean = false,
    contextStats: ContextStats? = null, currentChecklist: TaskChecklist? = null,
    detectedTemplate: ProjectTemplate? = null,
    onExecuteCommand: (String) -> Unit, onToggleClaudeMode: () -> Unit, onInstallClaude: () -> Unit = {},
    onReconnect: () -> Unit = {}, onClearConversation: () -> Unit = {},
    onToggleAutopilot: () -> Unit = {}, onSaveContext: () -> Unit = {}, onClearScreen: () -> Unit = {},
    onStopExecution: () -> Unit = {},
    onBack: () -> Unit, onOpenFiles: () -> Unit, onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }
    var showClearContextDialog by remember { mutableStateOf(false) }
    
    // Auto-scroll to bottom when new output arrives
    val outputSize = terminalOutput.size
    LaunchedEffect(outputSize) {
        try {
            if (outputSize > 0) {
                listState.animateScrollToItem(outputSize - 1)
            }
        } catch (e: Exception) { /* ignore scroll errors */ }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAudioPermission = it }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    // Enable partial results for continuous listening
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }
    var shouldKeepListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { isListening = true }
            override fun onEndOfSpeech() {
                // Don't stop - restart listening if user hasn't pressed stop
                if (shouldKeepListening) {
                    try { speechRecognizer.startListening(speechIntent) } catch (e: Exception) { isListening = false; shouldKeepListening = false }
                } else {
                    isListening = false
                }
            }
            override fun onError(e: Int) {
                // On error, try to restart if user wants to keep listening
                if (shouldKeepListening && (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    try { speechRecognizer.startListening(speechIntent) } catch (ex: Exception) { isListening = false; shouldKeepListening = false }
                } else {
                    isListening = false
                    shouldKeepListening = false
                }
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    inputText = if (inputText.isBlank()) it else "$inputText $it"
                }
                // Restart listening if user hasn't pressed stop
                if (shouldKeepListening) {
                    try { speechRecognizer.startListening(speechIntent) } catch (e: Exception) { isListening = false; shouldKeepListening = false }
                } else {
                    isListening = false
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(p: Bundle?) {
                // Show partial results in real-time (optional - can be distracting)
            }
            override fun onEvent(e: Int, p: Bundle?) {}
        })
        onDispose { speechRecognizer.destroy() }
    }
    
    val connectionColor = try { Color(android.graphics.Color.parseColor(connection.color)) } catch (e: Exception) { Primary }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(connection.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(currentPath, fontSize = 12.sp, color = OnSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = { onDisconnect(); onBack() }) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    // Copy all terminal output
                    IconButton(onClick = {
                        val allText = terminalOutput.joinToString("\n") { it.text }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", allText))
                        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copiar tudo") }
                    // Clear screen
                    IconButton(onClick = onClearScreen) { Icon(Icons.Default.Delete, "Limpar tela") }
                    // Open files
                    IconButton(onClick = onOpenFiles) { Icon(Icons.Default.Folder, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(if (isConnected) Success else Error)); Spacer(Modifier.width(8.dp)); Text(if (isConnected) "Conectado" else "Desconectado", fontSize = 12.sp, color = OnSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reconnect button when disconnected
                    if (!isConnected) {
                        Surface(onClick = onReconnect, shape = RoundedCornerShape(12.dp), color = Primary, enabled = !isReconnecting) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isReconnecting) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp), tint = Color.White)
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(if (isReconnecting) "Reconectando..." else "Reconectar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                    // Install Claude button
                    if (!hasClaudeCode && isConnected) {
                        Surface(onClick = onInstallClaude, shape = RoundedCornerShape(12.dp), color = ClaudeColor, enabled = !isInstallingClaude) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isInstallingClaude) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Download, null, Modifier.size(14.dp), tint = Color.White)
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(if (isInstallingClaude) "Instalando..." else "Instalar Claude", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                    // Claude mode toggle - always visible
                    Surface(
                        onClick = onToggleClaudeMode,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isClaudeMode) ClaudeColor else SurfaceVariant
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, null, Modifier.size(14.dp), tint = if (isClaudeMode) Color.White else OnSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isClaudeMode) "Claude" else "Terminal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isClaudeMode) Color.White else OnSurfaceVariant)
                        }
                    }
                    // Autopilot toggle - only when in Claude mode
                    if (isClaudeMode) {
                        Surface(
                            onClick = onToggleAutopilot,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAutopilot) Success else SurfaceVariant
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RocketLaunch, null, Modifier.size(14.dp), tint = if (isAutopilot) Color.White else OnSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text("Auto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isAutopilot) Color.White else OnSurfaceVariant)
                            }
                        }
                        // Context indicator (persisted or conversation)
                        if (hasConversationContext || hasPersistedContext) {
                            Surface(
                                onClick = { showClearContextDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasPersistedContext) NPMColor.copy(alpha = 0.8f) else PM2Color.copy(alpha = 0.8f)
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (hasPersistedContext) Icons.Default.Description else Icons.Default.History, null, Modifier.size(14.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (hasPersistedContext) "📄" else "${conversationMessageCount / 2} msgs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        // Save context button
                        if (conversationMessageCount > 0) {
                            Surface(
                                onClick = onSaveContext,
                                shape = RoundedCornerShape(12.dp),
                                color = NPMColor.copy(alpha = 0.6f)
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Save, null, Modifier.size(14.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                    // Only show spinner for reconnecting, not for loading (loading shows typing dots in chat)
                    if (isReconnecting && !isInstallingClaude) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                }
            }

            // Context stats indicator (shows when context is large or has been summarized)
            if (isClaudeMode && contextStats != null) {
                ContextStatsIndicator(contextStats)
            }

            // Project template indicator
            if (isClaudeMode && detectedTemplate != null && detectedTemplate.id != "generic") {
                ProjectTemplateIndicator(detectedTemplate)
            }

            // SelectionContainer allows text selection and copying
            // Wrap in Box with weight(1f) to ensure proper layout
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), state = listState) {
                        items(
                            count = terminalOutput.size,
                            key = { index -> "line_$index" }
                        ) { index ->
                            val line = try { terminalOutput[index] } catch (e: Exception) { return@items }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val baseColor = when (line.type) {
                                LineType.COMMAND -> Primary
                                LineType.SUCCESS -> Success
                                LineType.ERROR -> Error
                                LineType.CLAUDE -> ClaudeColor
                                LineType.INFO -> PM2Color
                                else -> OnSurface
                            }
                            // Use formatted text for OUTPUT type (Claude responses)
                            if (line.type == LineType.OUTPUT) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .combinedClickable(
                                            onClick = { },
                                            onLongClick = {
                                                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", line.text))
                                                Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                ) {
                                    FormattedTerminalLine(
                                        text = line.text,
                                        baseColor = baseColor
                                    )
                                }
                            } else {
                                // Command, error, info - use simple text
                                Text(
                                    text = line.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp,
                                    color = baseColor,
                                    fontWeight = if (line.type == LineType.COMMAND) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .combinedClickable(
                                            onClick = { },
                                            onLongClick = {
                                                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", line.text))
                                                Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                )
                            }
                        }
                        // Task checklist when available
                        if (currentChecklist != null) {
                            item(key = "task_checklist") {
                                TaskChecklistCard(currentChecklist)
                            }
                        }
                        // Typing indicator when loading
                        if (isLoading && isClaudeMode) {
                            item(key = "typing_indicator") {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }
            
            if (showQuickActions) Row(Modifier.fillMaxWidth().background(Surface).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                defaultQuickActions.forEach { action -> QuickActionButton(action) { onExecuteCommand(action.command) } }
            }
            
            Surface(Modifier.fillMaxWidth(), color = Surface) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = { showQuickActions = !showQuickActions }, Modifier.size(48.dp)) { Icon(if (showQuickActions) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null, tint = OnSurfaceVariant) }
                    OutlinedTextField(inputText, { inputText = it }, Modifier.weight(1f), placeholder = { Text(if (isClaudeMode) "Prompt para Claude..." else "Comando...", color = OnSurfaceVariant) }, leadingIcon = { Text(if (isClaudeMode) "🤖" else "$", fontSize = 16.sp) }, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = if (isClaudeMode) ClaudeColor else Primary, focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) { onExecuteCommand(inputText); inputText = "" } }), maxLines = 3)
                    IconButton(onClick = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (isListening) {
                            // Stop continuous listening
                            shouldKeepListening = false
                            speechRecognizer.stopListening()
                            isListening = false
                        } else {
                            // Start continuous listening
                            shouldKeepListening = true
                            speechRecognizer.startListening(speechIntent)
                        }
                    }, Modifier.size(48.dp).clip(CircleShape).background(if (isListening) Error else SurfaceVariant)) { Icon(if (isListening) Icons.Default.Mic else Icons.Default.MicNone, null, tint = Color.White) }
                    // Stop button - visible when loading
                    if (isLoading) {
                        IconButton(
                            onClick = onStopExecution,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(Error)
                        ) {
                            Icon(Icons.Default.Stop, "Parar execução", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { if (inputText.isNotBlank()) { onExecuteCommand(inputText); inputText = "" } }, Modifier.size(48.dp).clip(CircleShape).background(if (inputText.isNotBlank()) Primary else SurfaceVariant), enabled = inputText.isNotBlank()) { Icon(Icons.Default.Send, null, tint = Color.White) }
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing context
    if (showClearContextDialog) {
        AlertDialog(
            onDismissRequest = { showClearContextDialog = false },
            title = { Text("Limpar Contexto") },
            text = { Text("Deseja limpar o histórico da conversa? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearConversation()
                        showClearContextDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearContextDialog = false }) {
                    Text("Cancelar")
                }
            },
            containerColor = Surface
        )
    }
}

@Composable
fun QuickActionButton(action: QuickAction, onClick: () -> Unit) {
    val color = try { Color(action.color) } catch (e: Exception) { Primary }
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.2f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (action.icon) {
                "build" -> Icons.Default.Build
                "refresh" -> Icons.Default.Refresh
                "list" -> Icons.Default.List
                "cloud_download" -> Icons.Default.CloudDownload
                "smart_toy" -> Icons.Default.SmartToy
                "description" -> Icons.Default.Description
                else -> Icons.Default.PlayArrow
            }
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(action.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    // Elapsed time counter
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    // Status message based on elapsed time
    val statusMessage = when {
        elapsedSeconds < 3 -> "Conectando ao Claude..."
        elapsedSeconds < 10 -> "Claude está pensando..."
        elapsedSeconds < 30 -> "Processando solicitação..."
        elapsedSeconds < 60 -> "Gerando resposta... (pode levar mais tempo)"
        else -> "Ainda processando... (${elapsedSeconds}s)"
    }

    // Format elapsed time
    val timeDisplay = if (elapsedSeconds >= 60) {
        "${elapsedSeconds / 60}m ${elapsedSeconds % 60}s"
    } else {
        "${elapsedSeconds}s"
    }

    // Create animated alpha values for each dot with staggered delays
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = ClaudeColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 ", fontSize = 14.sp)
                    Text("●", color = ClaudeColor.copy(alpha = dot1Alpha), fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("●", color = ClaudeColor.copy(alpha = dot2Alpha), fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("●", color = ClaudeColor.copy(alpha = dot3Alpha), fontSize = 16.sp)
                }
                // Time elapsed badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceVariant
                ) {
                    Text(
                        text = timeDisplay,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusMessage,
                fontSize = 12.sp,
                color = ClaudeColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Card displaying task checklist with progress
 */
@Composable
fun TaskChecklistCard(checklist: TaskChecklist) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with title and progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📋", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        checklist.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }
                Text(
                    "${checklist.completedCount}/${checklist.steps.size}",
                    fontSize = 12.sp,
                    color = if (checklist.isComplete) Success else OnSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { checklist.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (checklist.isComplete) Success else ClaudeColor,
                trackColor = Surface
            )

            Spacer(Modifier.height(8.dp))

            // Task items
            checklist.steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, color) = when (step.status) {
                        TaskStatus.COMPLETED -> "✅" to Success
                        TaskStatus.IN_PROGRESS -> "🔄" to ClaudeColor
                        TaskStatus.FAILED -> "❌" to Error
                        TaskStatus.PENDING -> "⬜" to OnSurfaceVariant
                    }
                    Text(icon, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        step.description,
                        fontSize = 12.sp,
                        color = color,
                        fontWeight = if (step.status == TaskStatus.IN_PROGRESS) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2
                    )
                }
            }

            // Summary when complete
            if (checklist.isComplete && checklist.summary != null) {
                Spacer(Modifier.height(8.dp))
                Divider(color = Surface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "✨ ${checklist.summary}",
                    fontSize = 12.sp,
                    color = Success,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Compact context stats indicator
 */
@Composable
fun ContextStatsIndicator(stats: ContextStats) {
    if (stats.messageCount == 0 && !stats.hasSummary) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stats.hasSummary) {
                Text("📄", fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text("Resumido", fontSize = 10.sp, color = Success)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "${stats.messageCount} msgs · ${stats.totalChars / 1000}k chars",
                fontSize = 10.sp,
                color = OnSurfaceVariant
            )
        }
        if (stats.needsSummary) {
            Text(
                "⚠️ Contexto grande",
                fontSize = 10.sp,
                color = Warning
            )
        }
    }
}

/**
 * Compact project template indicator
 */
@Composable
fun ProjectTemplateIndicator(template: ProjectTemplate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Primary.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(template.icon, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            template.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Design System ativo",
            fontSize = 10.sp,
            color = OnSurfaceVariant
        )
    }
}

/**
 * Parse and format terminal text with colors for links, titles, code, etc.
 */
@Composable
fun FormattedTerminalLine(
    text: String,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val textLength = text.length

        // Patterns to match
        val patterns = listOf(
            // Links (https:// or http://)
            Triple(Regex("""https?://[^\s\])"'<>]+"""), TerminalLink, "link"),
            // Bold text **text**
            Triple(Regex("""\*\*([^*]+)\*\*"""), TerminalTitle, "bold"),
            // Inline code `code`
            Triple(Regex("""`([^`]+)`"""), TerminalCode, "code"),
            // Headers ## or ###
            Triple(Regex("""^#{1,3}\s+(.+)$""", RegexOption.MULTILINE), TerminalTitle, "header"),
            // File paths /path/to/file or ./relative
            Triple(Regex("""(?:^|[\s(])([./~][^\s:,)"']+\.[a-zA-Z0-9]+)"""), TerminalPath, "path"),
            // Numbers
            Triple(Regex("""\b(\d+(?:\.\d+)?)\b"""), TerminalNumber, "number")
        )

        // Find all matches and sort by position
        data class Match(val start: Int, val end: Int, val text: String, val color: Color, val tag: String, val displayText: String)
        val matches = mutableListOf<Match>()

        // Find links
        Regex("""https?://[^\s\])"'<>]+""").findAll(text).forEach { match ->
            matches.add(Match(match.range.first, match.range.last + 1, match.value, TerminalLink, "link", match.value))
        }

        // Find bold **text**
        Regex("""\*\*([^*]+)\*\*""").findAll(text).forEach { match ->
            val content = match.groupValues[1]
            matches.add(Match(match.range.first, match.range.last + 1, match.value, TerminalTitle, "bold", content))
        }

        // Find inline code `code`
        Regex("""`([^`]+)`""").findAll(text).forEach { match ->
            val content = match.groupValues[1]
            matches.add(Match(match.range.first, match.range.last + 1, match.value, TerminalCode, "code", content))
        }

        // Find headers (lines starting with # ## ###)
        if (text.startsWith("#")) {
            val headerMatch = Regex("""^#{1,3}\s+(.+)$""").find(text)
            if (headerMatch != null) {
                val content = headerMatch.groupValues[1]
                matches.add(Match(0, text.length, text, TerminalTitle, "header", content))
            }
        }

        // Sort by start position and filter overlapping
        val sortedMatches = matches.sortedBy { it.start }
        val filteredMatches = mutableListOf<Match>()
        var lastEnd = 0
        for (match in sortedMatches) {
            if (match.start >= lastEnd) {
                filteredMatches.add(match)
                lastEnd = match.end
            }
        }

        // Build annotated string
        var pos = 0
        for (match in filteredMatches) {
            // Add text before match
            if (match.start > pos) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(pos, match.start))
                }
            }

            // Add matched text with color
            when (match.tag) {
                "link" -> {
                    pushStringAnnotation(tag = "URL", annotation = match.displayText)
                    withStyle(SpanStyle(color = match.color, textDecoration = TextDecoration.Underline)) {
                        append(match.displayText)
                    }
                    pop()
                }
                "bold" -> {
                    withStyle(SpanStyle(color = match.color, fontWeight = FontWeight.Bold)) {
                        append(match.displayText)
                    }
                }
                "code" -> {
                    withStyle(SpanStyle(color = match.color, background = SurfaceVariant)) {
                        append(match.displayText)
                    }
                }
                "header" -> {
                    withStyle(SpanStyle(color = match.color, fontWeight = FontWeight.Bold)) {
                        append(match.displayText)
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = match.color)) {
                        append(match.displayText)
                    }
                }
            }
            pos = match.end
        }

        // Add remaining text
        if (pos < textLength) {
            withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(pos))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        ),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        // Ignore invalid URLs
                    }
                }
        }
    )
}
