package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Player",
    val photoUrl: String = "",
    val wins: Long = 0L,
    val losses: Long = 0L,
    val draws: Long = 0L,
    val gamesPlayed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostId: String = "",
    val creatorColor: String = "WHITE",
    val whitePlayerId: String = "",
    val blackPlayerId: String = "",
    val whiteName: String = "",
    val blackName: String = "",
    val whitePhotoUrl: String = "",
    val blackPhotoUrl: String = "",
    val participantIds: List<String> = emptyList(),
    val status: String = "WAITING", // WAITING, ACTIVE, FINISHED, EXPIRED
    val fen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val turn: String = "WHITE",
    val moveCount: Long = 0L,
    val lastMoveUci: String = "",
    val lastMoveSan: String = "",
    val uciHistory: List<String> = emptyList(),
    val sanHistory: List<String> = emptyList(),
    val timerMinutes: Long = 10L,
    val whiteTimeMs: Long = 600_000L,
    val blackTimeMs: Long = 600_000L,
    val winner: String = "",
    val termination: String = "",
    val drawOfferBy: String = "",
    val rematchRoomCode: String = "",
    val whiteLastSeenMs: Long = 0L,
    val blackLastSeenMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isParticipant(uid: String): Boolean = uid in participantIds

    fun playerColorFor(uid: String): String? = when (uid) {
        whitePlayerId -> "WHITE"
        blackPlayerId -> "BLACK"
        else -> null
    }
}
