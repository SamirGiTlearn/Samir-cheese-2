package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChessDao {

    @Query("SELECT * FROM offline_game_state WHERE id = :id LIMIT 1")
    fun observeOfflineGame(id: String = OfflineGameEntity.CURRENT_GAME_ID): Flow<OfflineGameEntity?>

    @Query("SELECT * FROM offline_game_state WHERE id = :id LIMIT 1")
    suspend fun getOfflineGameOnce(id: String = OfflineGameEntity.CURRENT_GAME_ID): OfflineGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOfflineGame(entity: OfflineGameEntity)

    @Query("DELETE FROM offline_game_state WHERE id = :id")
    suspend fun clearOfflineGame(id: String = OfflineGameEntity.CURRENT_GAME_ID)

    @Query("SELECT * FROM game_history ORDER BY playedAtMs DESC")
    fun observeGameHistory(): Flow<List<GameHistoryEntity>>

    @Query("SELECT * FROM game_history WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): GameHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameHistory(entity: GameHistoryEntity): Long

    @Query("DELETE FROM game_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM game_history")
    suspend fun clearAllHistory()
}
