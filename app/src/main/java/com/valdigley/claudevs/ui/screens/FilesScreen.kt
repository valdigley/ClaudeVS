package com.valdigley.claudevs.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valdigley.claudevs.service.FileItem
import com.valdigley.claudevs.ui.theme.*
import com.valdigley.claudevs.util.CrashLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    currentPath: String, files: List<FileItem>, isLoading: Boolean,
    onNavigateToFolder: (FileItem) -> Unit, onNavigateUp: () -> Unit, onNavigateHome: () -> Unit,
    onNavigateToPath: (String) -> Unit, onRefresh: () -> Unit, onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit, onDelete: (FileItem) -> Unit, onSetWorkingDir: () -> Unit, onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf("folder") }
    var newItemName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<FileItem?>(null) }

    // Log state for debugging
    LaunchedEffect(files.size, currentPath) {
        CrashLogger.log("FilesScreen", "Recomposed: path=$currentPath, files=${files.size}, loading=$isLoading")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arquivos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(Modifier.fillMaxWidth(), color = Surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onNavigateHome, Modifier.size(32.dp)) { Icon(Icons.Default.Home, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = onNavigateUp, Modifier.size(32.dp)) { Icon(Icons.Default.ArrowUpward, null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSetWorkingDir, Modifier.size(32.dp)) { Icon(Icons.Default.MyLocation, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                    }
                    // Breadcrumbs
                    val isHomePath = currentPath == "~" || currentPath.startsWith("~/")
                    val pathParts = if (isHomePath) {
                        if (currentPath == "~") listOf("~")
                        else listOf("~") + currentPath.removePrefix("~/").split("/").filter { it.isNotEmpty() }
                    } else {
                        currentPath.split("/").filter { it.isNotEmpty() }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!isHomePath) {
                            Text("/", Modifier.clickable { onNavigateToPath("/") }, fontSize = 14.sp, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        pathParts.forEachIndexed { index, part ->
                            if (index > 0 || !isHomePath) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = OnSurfaceVariant)
                            val targetPath = if (isHomePath) {
                                if (index == 0) "~" else "~/" + pathParts.drop(1).take(index).joinToString("/")
                            } else {
                                "/" + pathParts.take(index + 1).joinToString("/")
                            }
                            Text(part, Modifier.clickable { onNavigateToPath(targetPath) }, fontSize = 14.sp,
                                color = if (index == pathParts.lastIndex) OnBackground else Primary,
                                fontWeight = if (index == pathParts.lastIndex) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            if (isLoading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            else if (files.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.FolderOpen, null, Modifier.size(60.dp), tint = OnSurfaceVariant); Spacer(Modifier.height(16.dp)); Text("Pasta vazia", color = OnSurfaceVariant) } }
            else {
                LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        count = files.size,
                        key = { index -> "file_$index" }
                    ) { index ->
                        val file = try { files[index] } catch (e: Exception) {
                            CrashLogger.log("FilesScreen", "ERROR: file at index $index failed")
                            return@items
                        }
                        val fileName = file.name
                        val isDir = file.isDirectory
                        val fileSize = file.size

                        Card(
                            onClick = {
                                CrashLogger.log("FilesScreen", "Card clicked: $fileName, isDir=$isDir")
                                if (isDir) onNavigateToFolder(file)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Surface)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = if (isDir) StatusColor else OnSurfaceVariant
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(fileName, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = OnBackground)
                                    Text(if (isDir) "Pasta" else "$fileSize bytes", fontSize = 12.sp, color = OnSurfaceVariant)
                                }
                                if (isDir) Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant)
                                IconButton(onClick = { showDeleteDialog = file }) { Icon(Icons.Default.Delete, null, tint = Error) }
                            }
                        }
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), color = Surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { createType = "folder"; showCreateDialog = true }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = StatusColor), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("Nova Pasta") }
                    Button(onClick = { createType = "file"; showCreateDialog = true }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PM2Color), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.NoteAdd, null); Spacer(Modifier.width(8.dp)); Text("Novo Arquivo") }
                }
            }
        }
    }

    if (showCreateDialog) AlertDialog(
        onDismissRequest = { showCreateDialog = false; newItemName = "" },
        title = { Text(if (createType == "folder") "Nova Pasta" else "Novo Arquivo") },
        text = { OutlinedTextField(newItemName, { newItemName = it }, placeholder = { Text("Nome") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (newItemName.isNotBlank()) { if (createType == "folder") onCreateFolder(newItemName) else onCreateFile(newItemName); showCreateDialog = false; newItemName = "" } }) { Text("Criar", color = Primary) } },
        dismissButton = { TextButton(onClick = { showCreateDialog = false; newItemName = "" }) { Text("Cancelar") } },
        containerColor = Surface
    )

    showDeleteDialog?.let { file -> AlertDialog(
        onDismissRequest = { showDeleteDialog = null },
        title = { Text("Excluir ${file.name}?") },
        confirmButton = { TextButton(onClick = { onDelete(file); showDeleteDialog = null }, colors = ButtonDefaults.textButtonColors(contentColor = Error)) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") } },
        containerColor = Surface
    ) }
}
