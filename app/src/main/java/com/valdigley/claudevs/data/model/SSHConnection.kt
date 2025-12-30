package com.valdigley.claudevs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_connections")
data class SSHConnection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String? = null,
    val privateKey: String? = null,
    val color: String = "#4CAF50",
    val isDefault: Boolean = false,
    val lastConnected: Long? = null,
    val workingDirectory: String? = null,
    val anthropicApiKey: String? = null
)
