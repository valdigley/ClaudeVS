package com.valdigley.claudevs.data.database

import androidx.room.*
import com.valdigley.claudevs.data.model.DeveloperProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface DeveloperProfileDao {
    @Query("SELECT * FROM developer_profile WHERE id = 1")
    fun getProfile(): Flow<DeveloperProfile?>

    @Query("SELECT * FROM developer_profile WHERE id = 1")
    suspend fun getProfileSync(): DeveloperProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: DeveloperProfile)

    @Query("DELETE FROM developer_profile")
    suspend fun deleteProfile()
}
