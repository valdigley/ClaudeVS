package com.valdigley.claudevs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valdigley.claudevs.data.model.DeveloperProfile
import com.valdigley.claudevs.ui.theme.*

private val STYLE_OPTIONS = listOf("modern", "minimal", "classic", "bold")
private val FONT_OPTIONS = listOf("Inter", "Roboto", "Poppins", "Open Sans", "Montserrat", "Lato")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperProfileScreen(
    existingProfile: DeveloperProfile?,
    onSave: (DeveloperProfile) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var email by remember { mutableStateOf(existingProfile?.email ?: "") }
    var phone by remember { mutableStateOf(existingProfile?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(existingProfile?.whatsapp ?: "") }
    var ddd by remember { mutableStateOf(existingProfile?.ddd ?: "") }
    var city by remember { mutableStateOf(existingProfile?.city ?: "") }
    var state by remember { mutableStateOf(existingProfile?.state ?: "") }
    var country by remember { mutableStateOf(existingProfile?.country ?: "Brasil") }
    var company by remember { mutableStateOf(existingProfile?.company ?: "") }
    var website by remember { mutableStateOf(existingProfile?.website ?: "") }
    var github by remember { mutableStateOf(existingProfile?.github ?: "") }
    var linkedin by remember { mutableStateOf(existingProfile?.linkedin ?: "") }
    var primaryColor by remember { mutableStateOf(existingProfile?.primaryColor ?: "#4CAF50") }
    var secondaryColor by remember { mutableStateOf(existingProfile?.secondaryColor ?: "#2196F3") }
    var accentColor by remember { mutableStateOf(existingProfile?.accentColor ?: "#FF9800") }
    var preferredFontFamily by remember { mutableStateOf(existingProfile?.preferredFontFamily ?: "Inter") }
    var preferredStyle by remember { mutableStateOf(existingProfile?.preferredStyle ?: "modern") }
    var baseDomain by remember { mutableStateOf(existingProfile?.baseDomain ?: "") }
    var projectsBasePath by remember { mutableStateOf(existingProfile?.projectsBasePath ?: "") }
    var webServerType by remember { mutableStateOf(existingProfile?.webServerType ?: "nginx") }
    var sslEnabled by remember { mutableStateOf(existingProfile?.sslEnabled ?: true) }
    var copyrightText by remember { mutableStateOf(existingProfile?.copyrightText ?: "") }
    var copyrightYear by remember { mutableStateOf(existingProfile?.copyrightYear ?: "") }
    var customInstructions by remember { mutableStateOf(existingProfile?.customInstructions ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil do Desenvolvedor", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Info Section
            Text("Informacoes Pessoais", color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                placeholder = { Text("seu@email.com") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ddd, onValueChange = { ddd = it },
                    modifier = Modifier.width(80.dp),
                    label = { Text("DDD") },
                    placeholder = { Text("11") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Telefone") },
                    placeholder = { Text("99999-9999") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
                )
            }

            OutlinedTextField(
                value = whatsapp, onValueChange = { whatsapp = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WhatsApp (com DDI+DDD)") },
                placeholder = { Text("5511999999999") },
                leadingIcon = { Icon(Icons.Default.Chat, null, tint = Color(0xFF25D366)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF25D366), focusedContainerColor = Surface, unfocusedContainerColor = Surface),
                supportingText = { Text("Usado para botoes de WhatsApp", color = OnSurfaceVariant) }
            )

            Divider(color = SurfaceVariant)

            // Location Section
            Text("Localizacao", color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Cidade") },
                    leadingIcon = { Icon(Icons.Default.LocationCity, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
                )
                OutlinedTextField(
                    value = state, onValueChange = { state = it },
                    modifier = Modifier.width(100.dp),
                    label = { Text("Estado") },
                    placeholder = { Text("SP") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
                )
            }

            OutlinedTextField(
                value = country, onValueChange = { country = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pais") },
                leadingIcon = { Icon(Icons.Default.Public, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            Divider(color = SurfaceVariant)

            // Professional Section
            Text("Profissional", color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            OutlinedTextField(
                value = company, onValueChange = { company = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Empresa") },
                leadingIcon = { Icon(Icons.Default.Business, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            OutlinedTextField(
                value = website, onValueChange = { website = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Website") },
                placeholder = { Text("https://seusite.com") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            OutlinedTextField(
                value = github, onValueChange = { github = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub") },
                placeholder = { Text("github.com/usuario") },
                leadingIcon = { Icon(Icons.Default.Code, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            OutlinedTextField(
                value = linkedin, onValueChange = { linkedin = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("LinkedIn") },
                placeholder = { Text("linkedin.com/in/usuario") },
                leadingIcon = { Icon(Icons.Default.Work, null, tint = Color(0xFF0077B5)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0077B5), focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            Divider(color = SurfaceVariant)

            // Style Preferences Section
            Text("Preferencias de Estilo", color = ClaudeColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Estas configuracoes serao usadas pelo Claude ao criar projetos", color = OnSurfaceVariant, fontSize = 12.sp)

            // Colors
            Text("Cor Primaria", color = OnSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                COLORS.forEach { color ->
                    val c = try { Color(android.graphics.Color.parseColor(color)) } catch (e: Exception) { Primary }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (primaryColor == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable { primaryColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        if (primaryColor == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Text("Cor Secundaria", color = OnSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                COLORS.forEach { color ->
                    val c = try { Color(android.graphics.Color.parseColor(color)) } catch (e: Exception) { Primary }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (secondaryColor == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable { secondaryColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        if (secondaryColor == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Text("Cor de Destaque", color = OnSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                COLORS.forEach { color ->
                    val c = try { Color(android.graphics.Color.parseColor(color)) } catch (e: Exception) { Primary }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (accentColor == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable { accentColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        if (accentColor == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Font
            Text("Fonte Preferida", color = OnSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_OPTIONS.take(4).forEach { font ->
                    Surface(
                        onClick = { preferredFontFamily = font },
                        shape = RoundedCornerShape(8.dp),
                        color = if (preferredFontFamily == font) Primary else SurfaceVariant
                    ) {
                        Text(
                            font,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = if (preferredFontFamily == font) Color.White else OnSurfaceVariant
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_OPTIONS.drop(4).forEach { font ->
                    Surface(
                        onClick = { preferredFontFamily = font },
                        shape = RoundedCornerShape(8.dp),
                        color = if (preferredFontFamily == font) Primary else SurfaceVariant
                    ) {
                        Text(
                            font,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = if (preferredFontFamily == font) Color.White else OnSurfaceVariant
                        )
                    }
                }
            }

            // Style
            Text("Estilo Preferido", color = OnSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STYLE_OPTIONS.forEach { style ->
                    val label = when (style) {
                        "modern" -> "Moderno"
                        "minimal" -> "Minimalista"
                        "classic" -> "Classico"
                        "bold" -> "Marcante"
                        else -> style
                    }
                    Surface(
                        onClick = { preferredStyle = style },
                        shape = RoundedCornerShape(8.dp),
                        color = if (preferredStyle == style) ClaudeColor else SurfaceVariant
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = if (preferredStyle == style) Color.White else OnSurfaceVariant
                        )
                    }
                }
            }

            Divider(color = SurfaceVariant)

            // Hosting/Deploy Section
            Text("Hospedagem e Deploy", color = Success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Configure para criar subdominios automaticamente para cada projeto", color = OnSurfaceVariant, fontSize = 12.sp)

            OutlinedTextField(
                value = baseDomain, onValueChange = { baseDomain = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dominio Base") },
                placeholder = { Text("seudominio.com.br") },
                leadingIcon = { Icon(Icons.Default.Language, null, tint = Success) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Success, focusedContainerColor = Surface, unfocusedContainerColor = Surface),
                supportingText = { Text("Ex: projeto.seudominio.com.br sera criado automaticamente", color = OnSurfaceVariant) }
            )

            OutlinedTextField(
                value = projectsBasePath, onValueChange = { projectsBasePath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pasta Base dos Projetos") },
                placeholder = { Text("/var/www/sites") },
                leadingIcon = { Icon(Icons.Default.Folder, null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Success, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            Text("Servidor Web", color = OnSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("nginx", "apache", "caddy").forEach { server ->
                    Surface(
                        onClick = { webServerType = server },
                        shape = RoundedCornerShape(8.dp),
                        color = if (webServerType == server) Success else SurfaceVariant
                    ) {
                        Text(
                            server.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = if (webServerType == server) Color.White else OnSurfaceVariant
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SSL/HTTPS Ativado", color = OnSurfaceVariant)
                Switch(
                    checked = sslEnabled,
                    onCheckedChange = { sslEnabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Success)
                )
            }

            Divider(color = SurfaceVariant)

            // Copyright Section
            Text("Copyright/Rodape", color = PM2Color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            OutlinedTextField(
                value = copyrightText, onValueChange = { copyrightText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Texto de Copyright") },
                placeholder = { Text("Todos os direitos reservados a Seu Nome") },
                leadingIcon = { Icon(Icons.Default.Copyright, null, tint = PM2Color) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PM2Color, focusedContainerColor = Surface, unfocusedContainerColor = Surface),
                supportingText = { Text("Sera incluido automaticamente nos rodapes", color = OnSurfaceVariant) }
            )

            OutlinedTextField(
                value = copyrightYear, onValueChange = { copyrightYear = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ano (opcional)") },
                placeholder = { Text("2024 ou deixe vazio para ano atual") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PM2Color, focusedContainerColor = Surface, unfocusedContainerColor = Surface)
            )

            Divider(color = SurfaceVariant)

            // Custom Instructions
            Text("Instrucoes Personalizadas", color = ClaudeColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = customInstructions, onValueChange = { customInstructions = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("Ex: Sempre use TypeScript, prefira Tailwind CSS, inclua testes unitarios...") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClaudeColor, focusedContainerColor = Surface, unfocusedContainerColor = Surface),
                supportingText = { Text("Estas instrucoes serao enviadas ao Claude em cada projeto", color = OnSurfaceVariant) }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(
                        DeveloperProfile(
                            name = name,
                            email = email,
                            phone = phone,
                            whatsapp = whatsapp,
                            ddd = ddd,
                            city = city,
                            state = state,
                            country = country,
                            company = company,
                            website = website,
                            github = github,
                            linkedin = linkedin,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            accentColor = accentColor,
                            preferredFontFamily = preferredFontFamily,
                            preferredStyle = preferredStyle,
                            baseDomain = baseDomain,
                            projectsBasePath = projectsBasePath,
                            webServerType = webServerType,
                            sslEnabled = sslEnabled,
                            copyrightText = copyrightText,
                            copyrightYear = copyrightYear,
                            customInstructions = customInstructions
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Salvar Perfil")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
