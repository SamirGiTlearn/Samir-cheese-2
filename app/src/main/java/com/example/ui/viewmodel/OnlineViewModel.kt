package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChessSoundEffect
import com.example.audio.ChessSoundManager
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.ChessMove
import com.example.chess.engine.ChessPosition
import com.example.chess.engine.GameTermination
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.chess.engine.Square
import com.example.data.local.ChessDao
import com.example.data.local.GameHistoryEntity
import com.example.data.remote.OnlineChessRepository
import com.example.data.remote.OnlineRoom
import com.example.data.remote.UserProfile
import com.example.ui.components.PendingPromotion
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.coroutines.cancellation.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class OnlineMatchUiState(
    val room: OnlineRoom? = null,
    val position: ChessPosition = ChessEngine.initialPosition(),
    val selectedSquare: Square? = null,
    val legalMovesForSelected: List<ChessMove> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
    val localWhiteTimeMs: Long = 600_000L,
    val localBlackTimeMs: Long = 600_000L,
    val isSubmittingMove: Boolean = false,
    val isInCheck: Boolean = false,
    val errorMessage: String? = null,
    val showGameOverDialog: Boolean = false
)

class OnlineViewModel(
    private val onlineRepo: OnlineChessRepository,
    private val chessDao: ChessDao,
    private val soundManager: ChessSoundManager,
    val currentUserId: String,
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    val userProfile: StateFlow<UiState<UserProfile?>> = onlineRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val myRooms: StateFlow<UiState<List<OnlineRoom>>> = onlineRepo.observeMyRooms(currentUserId)
        .map<List<OnlineRoom>, UiState<List<OnlineRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing rooms", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load online rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _lobbyBusy = MutableStateFlow(false)
    val lobbyBusy: StateFlow<Boolean> = _lobbyBusy.asStateFlow()

    private val _lobbyError = MutableStateFlow<String?>(null)
    val lobbyError: StateFlow<String?> = _lobbyError.asStateFlow()

    private val _matchState = MutableStateFlow(OnlineMatchUiState())
    val matchState: StateFlow<OnlineMatchUiState> = _matchState.asStateFlow()

    private var roomObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var clockJob: Job? = null
    private var lastObservedMoveCount: Long = -1L
    private var recordedFinishedRoomCodes = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                onlineRepo.ensureUserProfile(user)
            }
        }
    }

    fun clearLobbyError() {
        _lobbyError.value = null
    }

    fun clearMatchError() {
        _matchState.update { it.copy(errorMessage = null) }
    }

    fun createPrivateRoom(
        creatorColor: PieceColor,
        timerMinutes: Int,
        onRoomCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _lobbyBusy.value = true
            _lobbyError.value = null
            val result = onlineRepo.createPrivateRoom(creatorColor, timerMinutes)
            _lobbyBusy.value = false
            result.onSuccess { code ->
                onRoomCreated(code)
            }.onFailure { err ->
                _lobbyError.value = err.localizedMessage ?: "Failed to create room"
            }
        }
    }

    fun joinRoomByCode(
        rawCode: String,
        onRoomJoined: (String) -> Unit
    ) {
        viewModelScope.launch {
            _lobbyBusy.value = true
            _lobbyError.value = null
            val result = onlineRepo.joinRoom(rawCode)
            _lobbyBusy.value = false
            result.onSuccess { room ->
                onRoomJoined(room.roomCode)
            }.onFailure { err ->
                _lobbyError.value = err.localizedMessage ?: "Unable to join room"
            }
        }
    }

    fun startObservingRoom(roomCode: String) {
        val normalized = OnlineChessRepository.normalizeRoomCode(roomCode)
        if (_matchState.value.room?.roomCode == normalized && roomObserverJob?.isActive == true) {
            return
        }
        stopObservingRoom()
        lastObservedMoveCount = -1L
        _matchState.value = OnlineMatchUiState()

        roomObserverJob = viewModelScope.launch {
            onlineRepo.observeRoom(normalized)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    _matchState.update {
                        it.copy(errorMessage = rootError.localizedMessage ?: "Connection interrupted")
                    }
                }
                .collect { room ->
                    if (room == null) {
                        _matchState.update {
                            it.copy(room = null, errorMessage = "Room closed or no longer available.")
                        }
                        return@collect
                    }

                    val pos = runCatching { ChessEngine.fromFen(room.fen) }
                        .getOrElse { ChessEngine.initialPosition() }
                    val inCheck = ChessEngine.isInCheck(pos, pos.turn)

                    if (lastObservedMoveCount >= 0 && room.moveCount > lastObservedMoveCount) {
                        when {
                            room.status == "FINISHED" -> soundManager.play(ChessSoundEffect.GAME_OVER)
                            inCheck -> soundManager.play(ChessSoundEffect.CHECK)
                            room.lastMoveSan.startsWith("O-") -> soundManager.play(ChessSoundEffect.CASTLE)
                            'x' in room.lastMoveSan -> soundManager.play(ChessSoundEffect.CAPTURE)
                            else -> soundManager.play(ChessSoundEffect.MOVE)
                        }
                    } else if (lastObservedMoveCount >= 0 && room.status == "FINISHED" &&
                        _matchState.value.room?.status != "FINISHED"
                    ) {
                        soundManager.play(ChessSoundEffect.GAME_OVER)
                    }
                    lastObservedMoveCount = room.moveCount

                    val showEndDialog = room.status == "FINISHED" &&
                        (_matchState.value.room?.status != "FINISHED" || _matchState.value.showGameOverDialog)

                    _matchState.update { prev ->
                        val prevRoom = prev.room
                        val shouldSyncClocks = prevRoom == null ||
                            room.moveCount != prevRoom.moveCount ||
                            room.status != prevRoom.status

                        val (syncedWhiteMs, syncedBlackMs) = if (shouldSyncClocks) {
                            var wMs = room.whiteTimeMs
                            var bMs = room.blackTimeMs
                            // Adjust active player's clock on mid-turn reconnect using server updatedAt
                            if (prevRoom == null && room.status == "ACTIVE" && room.updatedAt != null) {
                                val elapsedSinceUpdate = (timeProviderMs() - room.updatedAt.toDate().time)
                                    .coerceIn(0L, 3_600_000L)
                                if (room.turn == "WHITE") {
                                    wMs = (wMs - elapsedSinceUpdate).coerceAtLeast(0L)
                                } else {
                                    bMs = (bMs - elapsedSinceUpdate).coerceAtLeast(0L)
                                }
                            }
                            wMs to bMs
                        } else {
                            prev.localWhiteTimeMs to prev.localBlackTimeMs
                        }

                        prev.copy(
                            room = room,
                            position = pos,
                            selectedSquare = if (room.turn == room.playerColorFor(currentUserId)) prev.selectedSquare else null,
                            legalMovesForSelected = if (room.turn == room.playerColorFor(currentUserId)) prev.legalMovesForSelected else emptyList(),
                            localWhiteTimeMs = syncedWhiteMs,
                            localBlackTimeMs = syncedBlackMs,
                            isInCheck = inCheck,
                            errorMessage = null,
                            showGameOverDialog = showEndDialog
                        )
                    }

                    if (room.status == "FINISHED") {
                        recordMatchCompletedOnce(room)
                    }
                }
        }

        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(8_000L)
                val room = _matchState.value.room ?: continue
                if (room.status == "WAITING" || room.status == "ACTIVE") {
                    val isWhite = room.whitePlayerId == currentUserId
                    if (isWhite || room.blackPlayerId == currentUserId) {
                        onlineRepo.sendHeartbeat(room.roomCode, isWhite)
                    }
                }
            }
        }

        clockJob = viewModelScope.launch {
            var lastTickAtMs = timeProviderMs()
            var lastTimeoutClaimAtMs = 0L
            while (true) {
                delay(100L)
                val now = timeProviderMs()
                val elapsed = (now - lastTickAtMs).coerceIn(0L, 5_000L)
                lastTickAtMs = now

                val state = _matchState.value
                val room = state.room ?: continue
                if (room.status != "ACTIVE") {
                    lastTimeoutClaimAtMs = 0L
                    continue
                }
                if (elapsed <= 0L) continue

                val isWhiteTurn = room.turn == "WHITE"
                val nextW = if (isWhiteTurn) (state.localWhiteTimeMs - elapsed).coerceAtLeast(0L) else state.localWhiteTimeMs
                val nextB = if (!isWhiteTurn) (state.localBlackTimeMs - elapsed).coerceAtLeast(0L) else state.localBlackTimeMs
                _matchState.update { it.copy(localWhiteTimeMs = nextW, localBlackTimeMs = nextB) }

                if ((nextW <= 0L || nextB <= 0L) && (now - lastTimeoutClaimAtMs) >= 2_500L) {
                    lastTimeoutClaimAtMs = now
                    val timedOutColor = if (nextW <= 0L) PieceColor.WHITE else PieceColor.BLACK
                    val res = onlineRepo.claimTimeout(room.roomCode, timedOutColor, nextW, nextB)
                    if (res.isFailure) {
                        Log.w(TAG, "Timeout claim attempt failed, will retry", res.exceptionOrNull())
                    }
                }
            }
        }
    }

    fun stopObservingRoom() {
        roomObserverJob?.cancel()
        heartbeatJob?.cancel()
        clockJob?.cancel()
        roomObserverJob = null
        heartbeatJob = null
        clockJob = null
    }

    fun onSquareTapped(square: Square) {
        val state = _matchState.value
        val room = state.room ?: return
        if (room.status != "ACTIVE" || state.isSubmittingMove) return

        val myColorStr = room.playerColorFor(currentUserId) ?: return
        if (room.turn != myColorStr) return // Strictly block moves on opponent's turn!

        val myColor = PieceColor.fromString(myColorStr)
        val currentSelected = state.selectedSquare

        if (currentSelected != null && state.legalMovesForSelected.any { it.to == square }) {
            if (ChessEngine.needsPromotionSelection(state.position, currentSelected, square)) {
                _matchState.update {
                    it.copy(
                        pendingPromotion = PendingPromotion(
                            from = currentSelected,
                            to = square,
                            color = myColor
                        )
                    )
                }
            } else {
                submitOnlineMove(currentSelected, square, promotion = null)
            }
            return
        }

        val tappedPiece = state.position.pieceAt(square)
        if (tappedPiece != null && tappedPiece.color == myColor) {
            if (currentSelected == square) {
                _matchState.update { it.copy(selectedSquare = null, legalMovesForSelected = emptyList()) }
            } else {
                val legal = ChessEngine.legalMovesFrom(state.position, square)
                _matchState.update { it.copy(selectedSquare = square, legalMovesForSelected = legal) }
            }
        } else {
            _matchState.update { it.copy(selectedSquare = null, legalMovesForSelected = emptyList()) }
        }
    }

    fun onPromotionSelected(pieceType: PieceType) {
        val pending = _matchState.value.pendingPromotion ?: return
        _matchState.update { it.copy(pendingPromotion = null) }
        submitOnlineMove(pending.from, pending.to, pieceType)
    }

    fun dismissPromotion() {
        _matchState.update {
            it.copy(
                pendingPromotion = null,
                selectedSquare = null,
                legalMovesForSelected = emptyList()
            )
        }
    }

    private fun submitOnlineMove(from: Square, to: Square, promotion: PieceType?) {
        val state = _matchState.value
        val room = state.room ?: return
        viewModelScope.launch {
            _matchState.update {
                it.copy(
                    isSubmittingMove = true,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList()
                )
            }
            val result = onlineRepo.submitMove(
                roomCode = room.roomCode,
                from = from,
                to = to,
                promotion = promotion,
                updatedWhiteTimeMs = state.localWhiteTimeMs,
                updatedBlackTimeMs = state.localBlackTimeMs
            )
            _matchState.update { s ->
                s.copy(
                    isSubmittingMove = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun offerDraw() {
        val room = _matchState.value.room ?: return
        val myColor = room.playerColorFor(currentUserId) ?: return
        viewModelScope.launch {
            onlineRepo.offerOrCancelDraw(room.roomCode, myColor)
        }
    }

    fun declineDrawOffer() {
        val room = _matchState.value.room ?: return
        viewModelScope.launch {
            onlineRepo.offerOrCancelDraw(room.roomCode, "")
        }
    }

    fun acceptDrawOffer() {
        val room = _matchState.value.room ?: return
        viewModelScope.launch {
            onlineRepo.acceptDraw(room.roomCode)
        }
    }

    fun resignMatch() {
        val room = _matchState.value.room ?: return
        val myColorStr = room.playerColorFor(currentUserId) ?: return
        viewModelScope.launch {
            onlineRepo.resign(room.roomCode, PieceColor.fromString(myColorStr))
        }
    }

    fun cancelWaitingRoom(roomCode: String, onDone: () -> Unit) {
        viewModelScope.launch {
            onlineRepo.cancelWaitingRoom(roomCode)
            stopObservingRoom()
            onDone()
        }
    }

    fun requestRematch(onNavigateToRoom: (String) -> Unit) {
        val room = _matchState.value.room ?: return
        viewModelScope.launch {
            val result = onlineRepo.createRematchRoom(room)
            result.onSuccess { newCode ->
                onNavigateToRoom(newCode)
            }.onFailure { err ->
                _matchState.update { it.copy(errorMessage = err.localizedMessage ?: "Rematch failed") }
            }
        }
    }

    fun joinRematchRoom(rematchCode: String, onNavigateToRoom: (String) -> Unit) {
        joinRoomByCode(rematchCode, onNavigateToRoom)
    }

    fun dismissGameOverDialog() {
        _matchState.update { it.copy(showGameOverDialog = false) }
    }

    private fun recordMatchCompletedOnce(room: OnlineRoom) {
        if (!recordedFinishedRoomCodes.add(room.roomCode)) return
        val myColor = room.playerColorFor(currentUserId) ?: return
        val outcome = when {
            room.winner == "DRAW" -> "DRAW"
            room.winner == myColor -> "WIN"
            else -> "LOSS"
        }
        val resultString = when (room.winner) {
            "WHITE" -> "1-0"
            "BLACK" -> "0-1"
            else -> "1/2-1/2"
        }
        val termLabel = GameTermination.entries.firstOrNull { it.name == room.termination }?.label
            ?: room.termination

        viewModelScope.launch {
            onlineRepo.recordOnlineMatchResult(currentUserId, outcome)
            val pgn = ChessEngine.toPgn(
                whiteName = room.whiteName.ifBlank { "White" },
                blackName = room.blackName.ifBlank { "Black" },
                resultString = resultString,
                sanMoves = room.sanHistory
            )
            chessDao.insertGameHistory(
                GameHistoryEntity(
                    mode = "ONLINE",
                    whiteName = room.whiteName.ifBlank { "White" },
                    blackName = room.blackName.ifBlank { "Black" },
                    result = resultString,
                    winner = room.winner.ifBlank { "DRAW" },
                    termination = termLabel,
                    pgn = pgn,
                    uciHistoryJson = JSONArray(room.uciHistory).toString(),
                    sanHistoryJson = JSONArray(room.sanHistory).toString(),
                    finalFen = room.fen,
                    moveCount = room.sanHistory.size,
                    timerMinutes = room.timerMinutes.toInt()
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopObservingRoom()
    }

    private companion object {
        const val TAG = "OnlineViewModel"
    }
}
