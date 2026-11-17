package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.GameStatus
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.chess.engine.Square
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException

class OnlineChessRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val random = SecureRandom()

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Authenticated user mismatch" }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val safeName = (user.displayName?.trim()?.take(60)?.ifBlank { null }) ?: "Chess Player"
            val safePhoto = (user.photoUrl?.toString()?.take(500)) ?: ""
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "photoUrl" to safePhoto,
                    "wins" to 0L,
                    "losses" to 0L,
                    "draws" to 0L,
                    "gamesPlayed" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "photoUrl" to safePhoto,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Cannot observe another user's private profile" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordOnlineMatchResult(userId: String, outcome: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "User mismatch" }
            val docRef = db.collection("users").document(uid)
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val wins = (snap.getLong("wins") ?: 0L) + if (outcome == "WIN") 1L else 0L
                val losses = (snap.getLong("losses") ?: 0L) + if (outcome == "LOSS") 1L else 0L
                val draws = (snap.getLong("draws") ?: 0L) + if (outcome == "DRAW") 1L else 0L
                val games = (snap.getLong("gamesPlayed") ?: 0L) + 1L
                tx.update(
                    docRef,
                    mapOf(
                        "wins" to wins,
                        "losses" to losses,
                        "draws" to draws,
                        "gamesPlayed" to games,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createPrivateRoom(
        creatorColor: PieceColor,
        timerMinutes: Int
    ): Result<String> {
        val validTimer = if (timerMinutes in listOf(1, 3, 5, 10, 15)) timerMinutes else 10
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("User must be signed in with Google before creating a room.")
            val uid = user.uid
            val name = (user.displayName?.trim()?.take(60)?.ifBlank { null }) ?: "Player"
            val photo = (user.photoUrl?.toString()?.take(500)) ?: ""

            // Clean up any expired WAITING rooms hosted by this user
            cleanupExpiredHostRooms(uid)

            val roomCode = generateUniqueRoomCode()
            val roomRef = db.collection("rooms").document(roomCode)
            val nowMs = System.currentTimeMillis()
            val expiresAtMs = nowMs + ROOM_EXPIRATION_MS
            val initialTimeMs = validTimer * 60_000L

            val isWhiteCreator = creatorColor == PieceColor.WHITE
            val payload = mapOf(
                "roomCode" to roomCode,
                "hostId" to uid,
                "creatorColor" to creatorColor.name,
                "whitePlayerId" to if (isWhiteCreator) uid else "",
                "blackPlayerId" to if (!isWhiteCreator) uid else "",
                "whiteName" to if (isWhiteCreator) name else "",
                "blackName" to if (!isWhiteCreator) name else "",
                "whitePhotoUrl" to if (isWhiteCreator) photo else "",
                "blackPhotoUrl" to if (!isWhiteCreator) photo else "",
                "participantIds" to listOf(uid),
                "status" to "WAITING",
                "fen" to ChessEngine.INITIAL_FEN,
                "turn" to PieceColor.WHITE.name,
                "moveCount" to 0L,
                "lastMoveUci" to "",
                "lastMoveSan" to "",
                "uciHistory" to emptyList<String>(),
                "sanHistory" to emptyList<String>(),
                "timerMinutes" to validTimer.toLong(),
                "whiteTimeMs" to initialTimeMs,
                "blackTimeMs" to initialTimeMs,
                "winner" to "",
                "termination" to "",
                "drawOfferBy" to "",
                "rematchRoomCode" to "",
                "whiteLastSeenMs" to if (isWhiteCreator) nowMs else 0L,
                "blackLastSeenMs" to if (!isWhiteCreator) nowMs else 0L,
                "expiresAtMs" to expiresAtMs,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(payload).await()
            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "rooms")
            Result.failure(e)
        }
    }

    suspend fun joinRoom(rawRoomCode: String): Result<OnlineRoom> {
        val roomCode = normalizeRoomCode(rawRoomCode)
        if (roomCode.length < 4 || roomCode.length > 12) {
            return Result.failure(IllegalArgumentException("Enter a valid 6-character room code."))
        }
        val path = "rooms/$roomCode"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("User must be signed in with Google before joining a room.")
            val uid = user.uid
            val name = (user.displayName?.trim()?.take(60)?.ifBlank { null }) ?: "Player"
            val photo = (user.photoUrl?.toString()?.take(500)) ?: ""
            val roomRef = db.collection("rooms").document(roomCode)

            val joinedRoom = db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Room $roomCode was not found.")
                }
                val room = snap.toObject(OnlineRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Unable to read room $roomCode.")

                // Reconnect seamlessly if already a participant
                if (uid in room.participantIds) {
                    return@runTransaction room
                }

                if (room.status != "WAITING") {
                    throw IllegalStateException("This room is already ${room.status.lowercase()}.")
                }
                if (room.expiresAtMs > 0L && System.currentTimeMillis() > room.expiresAtMs) {
                    throw IllegalStateException("This room invitation has expired.")
                }
                if (room.participantIds.size >= 2) {
                    throw IllegalStateException("This room is already full.")
                }

                val nowMs = System.currentTimeMillis()
                val updatedParticipants = room.participantIds + uid
                val updates = if (room.whitePlayerId.isBlank()) {
                    mapOf(
                        "whitePlayerId" to uid,
                        "whiteName" to name,
                        "whitePhotoUrl" to photo,
                        "participantIds" to updatedParticipants,
                        "status" to "ACTIVE",
                        "whiteLastSeenMs" to nowMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                } else if (room.blackPlayerId.isBlank()) {
                    mapOf(
                        "blackPlayerId" to uid,
                        "blackName" to name,
                        "blackPhotoUrl" to photo,
                        "participantIds" to updatedParticipants,
                        "status" to "ACTIVE",
                        "blackLastSeenMs" to nowMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                } else {
                    throw IllegalStateException("Room already has two players.")
                }
                tx.update(roomRef, updates)

                room.copy(
                    whitePlayerId = if (room.whitePlayerId.isBlank()) uid else room.whitePlayerId,
                    whiteName = if (room.whitePlayerId.isBlank()) name else room.whiteName,
                    blackPlayerId = if (room.blackPlayerId.isBlank()) uid else room.blackPlayerId,
                    blackName = if (room.blackPlayerId.isBlank()) name else room.blackName,
                    participantIds = updatedParticipants,
                    status = "ACTIVE"
                )
            }.await()

            Result.success(joinedRoom)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeRoom(roomCode: String): Flow<OnlineRoom?> = flow {
        val uid = requireUserId()
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        emitAll(
            db.collection("rooms").document(normalized)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(OnlineRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<OnlineRoom>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Cannot query rooms for another user" }
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(OnlineRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Executes an atomic Firestore transaction that re-validates turn ownership and official FIDE
     * chess move legality against the authoritative server FEN before committing.
     */
    suspend fun submitMove(
        roomCode: String,
        from: Square,
        to: Square,
        promotion: PieceType?,
        updatedWhiteTimeMs: Long,
        updatedBlackTimeMs: Long
    ): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(normalized)
            val nowMs = System.currentTimeMillis()

            db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                val room = snap.toObject(OnlineRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Room not found")

                if (room.status != "ACTIVE") {
                    throw IllegalStateException("Game is not active")
                }
                if (uid !in room.participantIds) {
                    throw IllegalStateException("Not a participant in this room")
                }
                val expectedColor = PieceColor.fromString(room.turn)
                val playerColorStr = room.playerColorFor(uid)
                    ?: throw IllegalStateException("Player color not assigned")
                if (playerColorStr != expectedColor.name) {
                    throw IllegalStateException("Not your turn")
                }

                // Authoritative move & rules validation
                val position = ChessEngine.fromFen(room.fen)
                val legalMove = ChessEngine.findMatchingLegalMove(position, from, to, promotion)
                    ?: throw IllegalArgumentException("Illegal chess move: ${from.toAlgebraic()}${to.toAlgebraic()}")

                val nextPos = ChessEngine.applyMoveUnchecked(position, legalMove)
                val nextFen = ChessEngine.toFen(nextPos)
                val moveSan = ChessEngine.computeSan(position, legalMove, nextPos)
                val moveUci = legalMove.toUci()
                val newUciHistory = room.uciHistory + moveUci
                val newSanHistory = room.sanHistory + moveSan

                // Reconstruct repetition keys cleanly from initial position for threefold detection
                val repetitionKeys = computeRepetitionKeys(newUciHistory)
                val statusResult = ChessEngine.evaluateStatus(nextPos, repetitionKeys)

                val nextStatus: String
                val winner: String
                val termination: String
                when (statusResult) {
                    is GameStatus.Finished -> {
                        nextStatus = "FINISHED"
                        winner = statusResult.winner?.name ?: "DRAW"
                        termination = statusResult.termination.name
                    }
                    is GameStatus.Ongoing -> {
                        nextStatus = "ACTIVE"
                        winner = ""
                        termination = ""
                    }
                }

                val updates = mutableMapOf<String, Any>(
                    "fen" to nextFen,
                    "turn" to nextPos.turn.name,
                    "moveCount" to (room.moveCount + 1L),
                    "lastMoveUci" to moveUci,
                    "lastMoveSan" to moveSan,
                    "uciHistory" to newUciHistory,
                    "sanHistory" to newSanHistory,
                    "whiteTimeMs" to updatedWhiteTimeMs.coerceIn(0L, 3_600_000L),
                    "blackTimeMs" to updatedBlackTimeMs.coerceIn(0L, 3_600_000L),
                    "status" to nextStatus,
                    "winner" to winner,
                    "termination" to termination,
                    "drawOfferBy" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (expectedColor == PieceColor.WHITE) {
                    updates["whiteLastSeenMs"] = nowMs
                } else {
                    updates["blackLastSeenMs"] = nowMs
                }
                tx.update(roomRef, updates)
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(roomCode: String, isWhite: Boolean): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            requireUserId()
            val field = if (isWhite) "whiteLastSeenMs" else "blackLastSeenMs"
            db.collection("rooms").document(normalized)
                .update(
                    mapOf(
                        field to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Heartbeats may race with game end; log quietly without crashing UI
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun offerOrCancelDraw(roomCode: String, offerByColor: String): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            requireUserId()
            db.collection("rooms").document(normalized)
                .update(
                    mapOf(
                        "drawOfferBy" to offerByColor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptDraw(roomCode: String): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            requireUserId()
            db.collection("rooms").document(normalized)
                .update(
                    mapOf(
                        "status" to "FINISHED",
                        "winner" to "DRAW",
                        "termination" to "DRAW_AGREEMENT",
                        "drawOfferBy" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resign(roomCode: String, resigningColor: PieceColor): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            requireUserId()
            val winnerColor = resigningColor.opposite().name
            db.collection("rooms").document(normalized)
                .update(
                    mapOf(
                        "status" to "FINISHED",
                        "winner" to winnerColor,
                        "termination" to "RESIGNATION",
                        "drawOfferBy" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun claimTimeout(
        roomCode: String,
        timedOutColor: PieceColor,
        whiteTimeMs: Long,
        blackTimeMs: Long
    ): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            requireUserId()
            val winnerColor = timedOutColor.opposite().name
            db.collection("rooms").document(normalized)
                .update(
                    mapOf(
                        "status" to "FINISHED",
                        "winner" to winnerColor,
                        "termination" to "TIMEOUT",
                        "drawOfferBy" to "",
                        "whiteTimeMs" to whiteTimeMs.coerceAtLeast(0L),
                        "blackTimeMs" to blackTimeMs.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createRematchRoom(previousRoom: OnlineRoom): Result<String> {
        return try {
            val uid = requireUserId()
            if (previousRoom.rematchRoomCode.isNotBlank()) {
                return Result.success(previousRoom.rematchRoomCode)
            }
            val myPreviousColor = previousRoom.playerColorFor(uid)
            val nextCreatorColor = if (myPreviousColor == "WHITE") PieceColor.BLACK else PieceColor.WHITE
            val newCodeResult = createPrivateRoom(
                creatorColor = nextCreatorColor,
                timerMinutes = previousRoom.timerMinutes.toInt()
            )
            val newCode = newCodeResult.getOrElse { return Result.failure(it) }
            db.collection("rooms").document(previousRoom.roomCode)
                .update(
                    mapOf(
                        "rematchRoomCode" to newCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(newCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/${previousRoom.roomCode}")
            Result.failure(e)
        }
    }

    suspend fun cancelWaitingRoom(roomCode: String): Result<Unit> {
        val normalized = normalizeRoomCode(roomCode)
        val path = "rooms/$normalized"
        return try {
            requireUserId()
            db.collection("rooms").document(normalized).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun cleanupExpiredHostRooms(uid: String) {
        runCatching {
            val nowMs = System.currentTimeMillis()
            val snap = db.collection("rooms")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            for (doc in snap.documents) {
                val room = doc.toObject(OnlineRoom::class.java) ?: continue
                if (room.hostId == uid && room.status == "WAITING" && room.expiresAtMs in 1 until nowMs) {
                    doc.reference.delete().await()
                }
            }
        }
    }

    private suspend fun generateUniqueRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(8) {
            val candidate = buildString {
                repeat(6) { append(chars[random.nextInt(chars.length)]) }
            }
            val exists = runCatching {
                db.collection("rooms").document(candidate).get().await().exists()
            }.getOrDefault(false)
            if (!exists) return candidate
        }
        return buildString {
            repeat(8) { append(chars[random.nextInt(chars.length)]) }
        }
    }

    private fun computeRepetitionKeys(uciHistory: List<String>): List<String> {
        var pos = ChessEngine.initialPosition()
        val keys = mutableListOf(ChessEngine.repetitionKey(pos))
        for (uci in uciHistory) {
            val applied = ChessEngine.tryApplyUciMove(pos, uci) ?: break
            pos = applied.second
            keys.add(ChessEngine.repetitionKey(pos))
        }
        return keys
    }

    companion object {
        private const val ROOM_EXPIRATION_MS = 24L * 60L * 60L * 1000L

        fun normalizeRoomCode(input: String): String {
            // Support pasted deep link URLs as well as plain room codes
            val trimmed = input.trim()
            val extracted = when {
                "code=" in trimmed -> trimmed.substringAfter("code=").substringBefore("&")
                "/room/" in trimmed -> trimmed.substringAfterLast("/room/").substringBefore("?")
                else -> trimmed
            }
            return extracted.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
        }

        fun buildInvitationLink(roomCode: String): String =
            "https://chessmaster-samir.app/room/$roomCode"

        fun buildDeepLinkUri(roomCode: String): String =
            "chessmaster://join?code=$roomCode"
    }
}
