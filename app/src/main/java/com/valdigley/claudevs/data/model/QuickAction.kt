package com.valdigley.claudevs.data.model

data class QuickAction(
    val id: String,
    val label: String,
    val command: String,
    val icon: String,
    val color: Long
)

// Commands with "!" prefix execute directly in shell (bypass Claude mode)
val defaultQuickActions = listOf(
    QuickAction("1", "Build", "!npm run build", "build", 0xFF4CAF50),
    QuickAction("2", "PM2 Restart", "!pm2 restart all", "refresh", 0xFF2196F3),
    QuickAction("3", "PM2 Status", "!pm2 status", "list", 0xFFFF9800),
    QuickAction("4", "Git Pull", "!git pull origin main", "cloud_download", 0xFF9C27B0),
    QuickAction("5", "Logs", "!pm2 logs --lines 50", "description", 0xFF607D8B),
)
