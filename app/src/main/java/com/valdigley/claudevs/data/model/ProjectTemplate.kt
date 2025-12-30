package com.valdigley.claudevs.data.model

/**
 * Represents a project template with design system guidelines
 */
data class ProjectTemplate(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val detectFiles: List<String>, // Files that indicate this project type
    val context: String // Context to inject into Claude prompts
)

/**
 * Pre-defined project templates
 */
object ProjectTemplates {

    val REACT_VITE_TAILWIND = ProjectTemplate(
        id = "react-vite-tailwind",
        name = "React + Vite + Tailwind",
        icon = "⚛️",
        description = "React com Vite, TypeScript e Tailwind CSS",
        detectFiles = listOf("vite.config.ts", "vite.config.js", "tailwind.config.js", "tailwind.config.ts"),
        context = """
=== DESIGN SYSTEM: React + Vite + Tailwind ===

Stack: React 18+ | TypeScript | Vite | Tailwind CSS 3.4+ | Lucide React icons

CORES:
- Primary Cyan: #00D4D4 (principal)
- Primary Purple: #8B5CF6 (destaque/ações)
- Primary Pink: #EC4899 (acentos)
- Success: #4AE8C2
- Background Light: #F0F4F8 | Dark: #1A202C
- Cards Light: #FFFFFF | Dark: #2D3748

GRADIENTES:
- Principal: linear-gradient(135deg, #8B5CF6 0%, #EC4899 100%)
- Sucesso: linear-gradient(135deg, #00D4D4 0%, #4AE8C2 100%)

TIPOGRAFIA: font-family: 'Montserrat', sans-serif

COMPONENTES:
- Cards: bg-white dark:bg-gray-800 rounded-lg shadow-md p-4
- Botão Primary: bg-gradient-to-r from-purple-500 to-pink-500 text-white rounded-lg px-4 py-2
- Input: border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700
- Badge sucesso: bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200

DARK MODE: Usar classe 'dark' no html, prefixo dark: no Tailwind

RESPONSIVO: sm:640px | md:768px | lg:1024px | xl:1280px
===
""".trimIndent()
    )

    val NEXTJS = ProjectTemplate(
        id = "nextjs",
        name = "Next.js",
        icon = "▲",
        description = "Next.js com App Router",
        detectFiles = listOf("next.config.js", "next.config.mjs", "next.config.ts"),
        context = """
=== DESIGN SYSTEM: Next.js ===

Stack: Next.js 14+ | React 18+ | TypeScript | App Router

ESTRUTURA:
- app/ para rotas (App Router)
- components/ para componentes reutilizáveis
- lib/ para utilitários
- public/ para assets estáticos

PADRÕES:
- Use 'use client' apenas quando necessário
- Prefira Server Components
- Use Image do next/image para otimização
- Use Link do next/link para navegação
- Metadata API para SEO

ESTILIZAÇÃO: Tailwind CSS ou CSS Modules
===
""".trimIndent()
    )

    val NODE_EXPRESS = ProjectTemplate(
        id = "node-express",
        name = "Node.js + Express",
        icon = "🟢",
        description = "API Node.js com Express",
        detectFiles = listOf("express", "app.js", "server.js"),
        context = """
=== DESIGN SYSTEM: Node.js + Express API ===

Stack: Node.js | Express | TypeScript (opcional)

ESTRUTURA:
- src/routes/ - Definição de rotas
- src/controllers/ - Lógica de negócio
- src/middleware/ - Middlewares customizados
- src/models/ - Modelos de dados
- src/services/ - Serviços externos

PADRÕES:
- Use async/await para operações assíncronas
- Middleware de erro centralizado
- Validação de input com Joi ou Zod
- Respostas padronizadas: { success, data, error }
- Status codes HTTP corretos

SEGURANÇA:
- Helmet para headers de segurança
- Rate limiting
- Validação e sanitização de inputs
- CORS configurado corretamente
===
""".trimIndent()
    )

    val LANDING_PAGE = ProjectTemplate(
        id = "landing-page",
        name = "Landing Page",
        icon = "🌐",
        description = "Landing page estática ou com framework",
        detectFiles = listOf("index.html"),
        context = """
=== DESIGN SYSTEM: Landing Page ===

SEÇÕES TÍPICAS:
1. Hero - Título impactante + CTA principal
2. Features/Benefícios - Grid de cards
3. Social Proof - Depoimentos/logos de clientes
4. Pricing - Tabela de preços (se aplicável)
5. FAQ - Perguntas frequentes
6. CTA Final - Chamada para ação
7. Footer - Links, contato, copyright

CORES (usar as do Developer Profile se disponíveis):
- Primary: para CTAs e destaques
- Secondary: para elementos secundários
- Neutros: cinzas para texto e backgrounds

RESPONSIVO: Mobile-first, breakpoints em 768px e 1024px

PERFORMANCE:
- Lazy load para imagens abaixo do fold
- Fontes otimizadas (preload, display: swap)
- CSS crítico inline
===
""".trimIndent()
    )

    val SUPABASE = ProjectTemplate(
        id = "supabase",
        name = "Supabase",
        icon = "⚡",
        description = "Projeto com Supabase",
        detectFiles = listOf("supabase", ".supabase"),
        context = """
=== DESIGN SYSTEM: Supabase ===

Stack: Supabase (PostgreSQL, Auth, Storage, Realtime)

CLIENTE:
```typescript
import { createClient } from '@supabase/supabase-js'
const supabase = createClient(URL, ANON_KEY)
```

PADRÕES:
- RLS (Row Level Security) sempre habilitado
- Policies para controle de acesso
- Types gerados automaticamente
- Edge Functions para lógica serverless

AUTENTICAÇÃO:
- supabase.auth.signIn/signUp/signOut
- Providers: email, Google, GitHub, etc.
- Session management automático

QUERIES:
- .select() com tipagem
- .insert(), .update(), .delete()
- .eq(), .in(), .order(), .limit()
- Realtime: .on('postgres_changes')
===
""".trimIndent()
    )

    val GENERIC = ProjectTemplate(
        id = "generic",
        name = "Projeto Genérico",
        icon = "📁",
        description = "Projeto sem template específico",
        detectFiles = emptyList(),
        context = "" // No additional context
    )

    // All templates for detection
    val ALL_TEMPLATES = listOf(
        REACT_VITE_TAILWIND,
        NEXTJS,
        NODE_EXPRESS,
        SUPABASE,
        LANDING_PAGE
        // GENERIC is fallback, not in detection list
    )

    /**
     * Detect project type based on files present in directory
     */
    fun detectFromFiles(fileNames: List<String>): ProjectTemplate {
        // Check package.json content indicators
        val hasPackageJson = fileNames.any { it == "package.json" }

        // Priority detection
        for (template in ALL_TEMPLATES) {
            for (detectFile in template.detectFiles) {
                if (fileNames.any { it.contains(detectFile, ignoreCase = true) }) {
                    return template
                }
            }
        }

        // Fallback checks
        if (hasPackageJson) {
            // Could be any Node project
            if (fileNames.any { it == "index.html" }) {
                return LANDING_PAGE
            }
        }

        if (fileNames.any { it == "index.html" }) {
            return LANDING_PAGE
        }

        return GENERIC
    }
}
