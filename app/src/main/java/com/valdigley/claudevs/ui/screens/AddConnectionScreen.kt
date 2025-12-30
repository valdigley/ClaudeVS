package com.valdigley.claudevs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.valdigley.claudevs.data.model.SSHConnection
import com.valdigley.claudevs.ui.theme.*

val COLORS = listOf("#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#E91E63", "#00BCD4", "#FF5722", "#607D8B")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionScreen(
    existingConnection: SSHConnection? = null,
    onSave: (SSHConnection) -> Unit,
    onBack: () -> Unit,
    onTest: (SSHConnection) -> Unit
) {
    var name by remember { mutableStateOf(existingConnection?.name ?: "") }
    var host by remember { mutableStateOf(existingConnection?.host ?: "") }
    var port by remember { mutableStateOf(existingConnection?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(existingConnection?.username ?: "") }
    var password by remember { mutableStateOf(existingConnection?.password ?: "") }
    var workingDirectory by remember { mutableStateOf(existingConnection?.workingDirectory ?: "") }
    var selectedColor by remember { mutableStateOf(existingConnection?.color ?: COLORS[0]) }
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingConnection != null) "Editar" else "Nova Conexão", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, placeholder = { Text("Ex: VPS Triagem") },
                leadingIcon = { Icon(Icons.Default.Label, null) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface))
            
            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("Host") }, placeholder = { Text("192.168.1.1") },
                leadingIcon = { Icon(Icons.Default.Dns, null) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface))
            
            OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), label = { Text("Porta") },
                leadingIcon = { Icon(Icons.Default.Cable, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface))
            
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Usuário") }, placeholder = { Text("root") },
                leadingIcon = { Icon(Icons.Default.Person, null) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface))
            
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Senha") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface))

            OutlinedTextField(workingDirectory, { workingDirectory = it }, Modifier.fillMaxWidth(), label = { Text("Diretório de Trabalho") }, placeholder = { Text("/home/user/projeto") },
                leadingIcon = { Icon(Icons.Default.Folder, null) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface))

            Text("Cor", color = OnSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                COLORS.forEach { color ->
                    val c = try { Color(android.graphics.Color.parseColor(color)) } catch (e: Exception) { Primary }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c).then(if (selectedColor == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier).clickable { selectedColor = color },
                        contentAlignment = Alignment.Center) { if (selectedColor == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedButton(onClick = { onTest(SSHConnection(existingConnection?.id ?: 0, name, host, port.toIntOrNull() ?: 22, username, password, null, selectedColor, false, null, workingDirectory.ifBlank { null })) },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = PM2Color)) {
                Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.width(8.dp)); Text("Testar Conexão")
            }

            Button(onClick = { onSave(SSHConnection(existingConnection?.id ?: 0, name, host, port.toIntOrNull() ?: 22, username, password, null, selectedColor, existingConnection?.isDefault ?: false, existingConnection?.lastConnected, workingDirectory.ifBlank { null })) },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text(if (existingConnection != null) "Salvar" else "Adicionar")
            }
        }
    }
}
