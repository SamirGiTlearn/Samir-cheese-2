package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_game_state")
data class OfflineGameEntity(
    @PrimaryKey val id: String = CURRENT_GAME_ID,
    val fen: String,
    val uciHistoryJson: String,
    val sanHistoryJson: String,
    val fenHistoryJson: String,
    val whiteName: String,
    val blackName: String,
    val timerMinutes: Int,
    val whiteTimeMs: Long,
    val blackTimeMs: Long,
    val status: String, // ONGOING, FINISHED
    val winner: String, // "", WHITE, BLACK, DRAW
    val termination: String,
    val updatedAtMs: Long
) {
    companion object {
        const val CURRENT_GAME_ID = "current_offline_game"
    }
}

@Entity(tableName = "game_history")
data class GameHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mode: String, // OFFLINE, ONLINE
    val whiteName: String,
    val blackName: String,
    val result: String, // "1-0", "0-1", "1/2-1/2"
    val winner: String, // WHITE, BLACK, DRAW
    val termination: String,
    val pgn: String,
    val uciHistoryJson: String,
    val sanHistoryJson: String,
    val finalFen: String,
    val moveCount: Int,
    val timerMinutes: Int,
    val playedAtMs: Long = System.currentTimeMillis()
)
