package com.valdigley.claudevs.data.database

import androidx.room.*
import com.valdigley.claudevs.data.model.SSHConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM ssh_connections ORDER BY lastConnected DESC")
    fun getAllConnections(): Flow<List<SSHConnection>>
    
    @Query("SELECT * FROM ssh_connections WHERE id = :id")
    suspend fun getConnectionById(id: Long): SSHConnection?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: SSHConnection): Long
    
    @Update
    suspend fun updateConnection(connection: SSHConnection)
    
    @Delete
    suspend fun deleteConnection(connection: SSHConnection)
    
    @Query("UPDATE ssh_connections SET isDefault = 0")
    suspend fun clearDefaultConnection()
    
    @Query("UPDATE ssh_connections SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultConnection(id: Long)
    
    @Query("UPDATE ssh_connections SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)
}
