package com.valdigley.claudevs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valdigley.claudevs.data.model.SSHConnection
import com.valdigley.claudevs.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    connections: List<SSHConnection>,
    onConnectionClick: (SSHConnection) -> Unit,
    onFilesClick: (SSHConnection) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (SSHConnection) -> Unit,
    onDeleteClick: (SSHConnection) -> Unit,
    onSetDefault: (SSHConnection) -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf<SSHConnection?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClaudeVS", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = OnBackground),
                actions = {
                    var showLogDialog by remember { mutableStateOf(false) }
                    val clipboardManager = LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }

                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, "Perfil", tint = ClaudeColor)
                    }
                    IconButton(onClick = { showLogDialog = true; copied = false }) {
                        Icon(Icons.Default.BugReport, "Ver Log")
                    }
                    if (showLogDialog) {
                        val logContent = com.valdigley.claudevs.util.CrashLogger.getLogContent()
                        AlertDialog(
                            onDismissRequest = { showLogDialog = false },
                            title = { Text("Debug Log") },
                            text = {
                                Column {
                                    if (copied) {
                                        Text("✅ Copiado!", color = Success, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    }
                                    LazyColumn(Modifier.height(350.dp)) {
                                        item { Text(logContent, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                                    }
                                }
                            },
                            confirmButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(logContent))
                                        copied = true
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copiar")
                                    }
                                    TextButton(onClick = { com.valdigley.claudevs.util.CrashLogger.clearLog(); showLogDialog = false }) {
                                        Text("Limpar")
                                    }
                                }
                            },
                            dismissButton = { TextButton(onClick = { showLogDialog = false }) { Text("Fechar") } }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick, containerColor = Primary, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        },
        containerColor = Background
    ) { paddingValues ->
        if (connections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Dns, null, Modifier.size(80.dp), tint = OnSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhuma conexão", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                    Spacer(Modifier.height(8.dp))
                    Text("Adicione suas VPS para começar", fontSize = 14.sp, color = OnSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar VPS")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(connections) { connection ->
                    ConnectionCard(connection, { onConnectionClick(connection) }, { onFilesClick(connection) }, { onEditClick(connection) }, { showDeleteDialog = connection }, { onSetDefault(connection) })
                }
            }
        }
    }
    
    showDeleteDialog?.let { connection ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Excluir ${connection.name}?") },
            confirmButton = { TextButton(onClick = { onDeleteClick(connection); showDeleteDialog = null }, colors = ButtonDefaults.textButtonColors(contentColor = Error)) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") } },
            containerColor = Surface
        )
    }
}

@Composable
fun ConnectionCard(connection: SSHConnection, onClick: () -> Unit, onFiles: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    val color = try { Color(android.graphics.Color.parseColor(connection.color)) } catch (e: Exception) { Primary }
    
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(50.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Default.Dns, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(connection.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OnBackground)
                    if (connection.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFD700)) {
                            Text("Padrão", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
                Text("${connection.username}@${connection.host}:${connection.port}", fontSize = 12.sp, color = OnSurfaceVariant)
            }
            IconButton(onClick = onSetDefault) { Icon(if (connection.isDefault) Icons.Default.Star else Icons.Default.StarOutline, null, tint = if (connection.isDefault) Color(0xFFFFD700) else OnSurfaceVariant) }
            IconButton(onClick = onFiles) { Icon(Icons.Default.Folder, null, tint = StatusColor) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = PM2Color) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Error) }
        }
    }
}
