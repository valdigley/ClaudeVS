package com.valdigley.claudevs.data.database

import androidx.room.*
import com.valdigley.claudevs.data.model.CommandHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<CommandHistory>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: CommandHistory): Long
    
    @Query("DELETE FROM command_history")
    suspend fun clearAllHistory()
}
