package com.valdigley.claudevs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "developer_profile")
data class DeveloperProfile(
    @PrimaryKey
    val id: Int = 1, // Singleton - only one profile
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val ddd: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "Brasil",
    // Style preferences
    val primaryColor: String = "#4CAF50",
    val secondaryColor: String = "#2196F3",
    val accentColor: String = "#FF9800",
    val preferredFontFamily: String = "Inter",
    val preferredStyle: String = "modern", // modern, minimal, classic, bold
    // Additional info
    val company: String = "",
    val website: String = "",
    val github: String = "",
    val linkedin: String = "",
    // Hosting/Deploy settings
    val baseDomain: String = "",           // Ex: valdigley.com.br
    val projectsBasePath: String = "",     // Ex: /var/www/sites
    val webServerType: String = "nginx",   // nginx, apache, caddy
    val sslEnabled: Boolean = true,
    // Copyright/Footer settings
    val copyrightText: String = "",        // Ex: Todos os direitos reservados a Valdigley Santos
    val copyrightYear: String = "",        // Ex: 2024 ou deixar vazio para ano atual
    // Custom instructions for Claude
    val customInstructions: String = DEFAULT_INSTRUCTIONS
) {
    companion object {
        const val DEFAULT_INSTRUCTIONS = """Apos criar qualquer projeto ou fazer alteracoes:
1. Execute npm run build ou comando de build equivalente
2. Verifique logs com pm2 logs --lines 20
3. Teste a aplicacao acessando a URL se disponivel
4. Se houver erros, corrija automaticamente
5. Repita ate funcionar corretamente

Sempre valide o resultado antes de finalizar.
Ao concluir, forneca um resumo do que foi implementado."""
    }
    fun toContextString(): String {
        val parts = mutableListOf<String>()

        if (name.isNotBlank()) parts.add("Developer: $name")
        if (email.isNotBlank()) parts.add("Email: $email")
        if (phone.isNotBlank()) parts.add("Phone: $phone")
        if (whatsapp.isNotBlank()) parts.add("WhatsApp: $whatsapp")
        if (ddd.isNotBlank()) parts.add("DDD: $ddd")
        if (city.isNotBlank() || state.isNotBlank()) {
            val location = listOfNotNull(
                city.takeIf { it.isNotBlank() },
                state.takeIf { it.isNotBlank() },
                country.takeIf { it.isNotBlank() }
            ).joinToString(", ")
            parts.add("Location: $location")
        }
        if (company.isNotBlank()) parts.add("Company: $company")
        if (website.isNotBlank()) parts.add("Website: $website")
        if (github.isNotBlank()) parts.add("GitHub: $github")

        // Style preferences
        val stylePrefs = mutableListOf<String>()
        stylePrefs.add("Primary color: $primaryColor")
        stylePrefs.add("Secondary color: $secondaryColor")
        stylePrefs.add("Accent color: $accentColor")
        stylePrefs.add("Font: $preferredFontFamily")
        stylePrefs.add("Style: $preferredStyle")
        parts.add("Design preferences: ${stylePrefs.joinToString(", ")}")

        // Hosting/Deploy settings
        if (baseDomain.isNotBlank()) {
            parts.add("\n--- HOSTING CONFIGURATION ---")
            parts.add("Base domain for projects: $baseDomain")
            parts.add("IMPORTANT: When creating web projects (landing pages, sites, apps), automatically create a subdomain like: projeto.$baseDomain")
            if (projectsBasePath.isNotBlank()) {
                parts.add("Projects base path: $projectsBasePath (create project folder here)")
            }
            parts.add("Web server: $webServerType")
            parts.add("SSL: ${if (sslEnabled) "enabled (use HTTPS)" else "disabled"}")
            parts.add("After creating the project, configure the web server to serve it and provide the live URL")
        }

        // Copyright settings
        if (copyrightText.isNotBlank()) {
            parts.add("\n--- COPYRIGHT/FOOTER ---")
            val year = if (copyrightYear.isNotBlank()) copyrightYear else java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
            parts.add("Copyright text: © $year $copyrightText")
            parts.add("IMPORTANT: Include this copyright in all website footers and project descriptions")
        }

        if (customInstructions.isNotBlank()) {
            parts.add("\n--- CUSTOM INSTRUCTIONS ---")
            parts.add(customInstructions)
        }

        return if (parts.isNotEmpty()) {
            "=== DEVELOPER PROFILE ===\n${parts.joinToString("\n")}\n========================="
        } else ""
    }
}
