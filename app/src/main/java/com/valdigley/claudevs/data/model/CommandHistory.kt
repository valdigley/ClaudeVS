package com.valdigley.claudevs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val output: String,
    val timestamp: Long = System.currentTimeMillis(),
    val connectionId: Long,
    val isClaudeCode: Boolean = false,
    val status: String = "success"
)
