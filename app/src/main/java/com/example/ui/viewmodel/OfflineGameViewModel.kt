package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChessSoundEffect
import com.example.audio.ChessSoundManager
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.ChessMove
import com.example.chess.engine.ChessPosition
import com.example.chess.engine.GameStatus
import com.example.chess.engine.GameTermination
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.chess.engine.Square
import com.example.data.local.ChessDao
import com.example.data.local.GameHistoryEntity
import com.example.data.local.OfflineGameEntity
import com.example.data.local.UserPreferencesRepository
import com.example.ui.components.PendingPromotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

data class OfflineUiState(
    val isInitialized: Boolean = false,
    val position: ChessPosition = ChessEngine.initialPosition(),
    val uciHistory: List<String> = emptyList(),
    val sanHistory: List<String> = emptyList(),
    val fenHistory: List<String> = listOf(ChessEngine.INITIAL_FEN),
    val selectedSquare: Square? = null,
    val legalMovesForSelected: List<ChessMove> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
    val lastMoveFrom: Square? = null,
    val lastMoveTo: Square? = null,
    val whiteName: String = "Player 1 (White)",
    val blackName: String = "Player 2 (Black)",
    val timerMinutes: Int = 10,
    val whiteTimeMs: Long = 600_000L,
    val blackTimeMs: Long = 600_000L,
    val status: GameStatus = GameStatus.Ongoing(inCheck = false),
    val isBoardFlipped: Boolean = false,
    val isPaused: Boolean = false,
    val showGameOverDialog: Boolean = false,
    val showNewGameDialog: Boolean = false,
    val statusBannerMessage: String? = null
)

class OfflineGameViewModel(
    private val chessDao: ChessDao,
    private val prefsRepo: UserPreferencesRepository,
    private val soundManager: ChessSoundManager,
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineUiState())
    val uiState: StateFlow<OfflineUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var historySavedForCurrentGame = false

    init {
        loadSavedOrStartFresh()
    }

    private fun loadSavedOrStartFresh() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferences.first()
            soundManager.isSoundEnabled = prefs.soundEnabled
            val saved = chessDao.getOfflineGameOnce()
            if (saved != null) {
                val position = runCatching { ChessEngine.fromFen(saved.fen) }
                    .getOrElse { ChessEngine.initialPosition() }
                val uciList = decodeStringList(saved.uciHistoryJson)
                val sanList = decodeStringList(saved.sanHistoryJson)
                val fenList = decodeStringList(saved.fenHistoryJson).ifEmpty { listOf(ChessEngine.INITIAL_FEN) }
                val lastUci = uciList.lastOrNull()
                val lastFrom = lastUci?.take(2)?.let { Square.fromAlgebraic(it) }
                val lastTo = lastUci?.drop(2)?.take(2)?.let { Square.fromAlgebraic(it) }

                val status = when {
                    saved.status == "FINISHED" -> {
                        val winner = when (saved.winner) {
                            "WHITE" -> PieceColor.WHITE
                            "BLACK" -> PieceColor.BLACK
                            else -> null
                        }
                        val term = GameTermination.entries.firstOrNull { it.name == saved.termination }
                            ?: GameTermination.CHECKMATE
                        historySavedForCurrentGame = true
                        GameStatus.Finished(winner, term)
                    }
                    saved.whiteTimeMs <= 0L -> {
                        historySavedForCurrentGame = false
                        GameStatus.Finished(PieceColor.BLACK, GameTermination.TIMEOUT)
                    }
                    saved.blackTimeMs <= 0L -> {
                        historySavedForCurrentGame = false
                        GameStatus.Finished(PieceColor.WHITE, GameTermination.TIMEOUT)
                    }
                    else -> {
                        historySavedForCurrentGame = false
                        val repKeys = computeRepetitionKeys(fenList)
                        ChessEngine.evaluateStatus(position, repKeys)
                    }
                }

                _uiState.value = OfflineUiState(
                    isInitialized = true,
                    position = position,
                    uciHistory = uciList,
                    sanHistory = sanList,
                    fenHistory = fenList,
                    lastMoveFrom = lastFrom,
                    lastMoveTo = lastTo,
                    whiteName = saved.whiteName.ifBlank { prefs.player1Name },
                    blackName = saved.blackName.ifBlank { prefs.player2Name },
                    timerMinutes = saved.timerMinutes,
                    whiteTimeMs = saved.whiteTimeMs,
                    blackTimeMs = saved.blackTimeMs,
                    status = status,
                    isBoardFlipped = prefs.autoFlipOfflineBoard && position.turn == PieceColor.BLACK,
                    isPaused = false,
                    showGameOverDialog = status is GameStatus.Finished
                )
                if (status is GameStatus.Finished && saved.status != "FINISHED") {
                    persistState()
                    archiveFinishedGameIfNeeded()
                }
            } else {
                startNewGameInternal(
                    timerMinutes = prefs.defaultTimerMinutes,
                    whiteName = prefs.player1Name,
                    blackName = prefs.player2Name
                )
            }
            startClockLoop()
        }
    }

    private fun startClockLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var lastTickAtMs = timeProviderMs()
            var tickCount = 0
            while (true) {
                delay(100L)
                val now = timeProviderMs()
                val elapsed = (now - lastTickAtMs).coerceIn(0L, 5_000L)
                lastTickAtMs = now
                if (elapsed <= 0L) continue

                val ticked = applyClockElapsed(elapsed)
                if (ticked) {
                    tickCount++
                    if (_uiState.value.status is GameStatus.Ongoing && tickCount % 20 == 0) {
                        persistState()
                    }
                }
            }
        }
    }

    internal suspend fun applyClockElapsed(elapsedMs: Long): Boolean {
        val state = _uiState.value
        if (!state.isInitialized || state.isPaused || state.status !is GameStatus.Ongoing) {
            return false
        }
        if (elapsedMs <= 0L) return false

        val activeTurn = state.position.turn
        val nextWhite = if (activeTurn == PieceColor.WHITE) {
            (state.whiteTimeMs - elapsedMs).coerceAtLeast(0L)
        } else {
            state.whiteTimeMs
        }
        val nextBlack = if (activeTurn == PieceColor.BLACK) {
            (state.blackTimeMs - elapsedMs).coerceAtLeast(0L)
        } else {
            state.blackTimeMs
        }

        if (nextWhite <= 0L || nextBlack <= 0L) {
            val winner = if (nextWhite <= 0L) PieceColor.BLACK else PieceColor.WHITE
            val finished = GameStatus.Finished(winner, GameTermination.TIMEOUT)
            _uiState.update {
                it.copy(
                    whiteTimeMs = nextWhite,
                    blackTimeMs = nextBlack,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    pendingPromotion = null,
                    status = finished,
                    isPaused = false,
                    showGameOverDialog = true
                )
            }
            soundManager.play(ChessSoundEffect.GAME_OVER)
            persistState()
            archiveFinishedGameIfNeeded()
        } else {
            _uiState.update { it.copy(whiteTimeMs = nextWhite, blackTimeMs = nextBlack) }
        }
        return true
    }

    fun togglePause() {
        _uiState.update { state ->
            if (state.status !is GameStatus.Ongoing) state
            else state.copy(isPaused = !state.isPaused)
        }
    }

    internal fun stopClock() {
        timerJob?.cancel()
        timerJob = null
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        if (!state.isInitialized || state.status !is GameStatus.Ongoing) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
        }

        val tappedPiece = state.position.pieceAt(square)
        val currentSelected = state.selectedSquare

        // If tapping a legal destination square for selected piece
        if (currentSelected != null && state.legalMovesForSelected.any { it.to == square }) {
            if (ChessEngine.needsPromotionSelection(state.position, currentSelected, square)) {
                _uiState.update {
                    it.copy(
                        pendingPromotion = PendingPromotion(
                            from = currentSelected,
                            to = square,
                            color = state.position.turn
                        )
                    )
                }
            } else {
                executeMove(currentSelected, square, promotion = null)
            }
            return
        }

        // Select or switch selection to own piece
        if (tappedPiece != null && tappedPiece.color == state.position.turn) {
            if (currentSelected == square) {
                _uiState.update { it.copy(selectedSquare = null, legalMovesForSelected = emptyList()) }
            } else {
                val legal = ChessEngine.legalMovesFrom(state.position, square)
                _uiState.update { it.copy(selectedSquare = square, legalMovesForSelected = legal) }
            }
        } else {
            _uiState.update { it.copy(selectedSquare = null, legalMovesForSelected = emptyList()) }
        }
    }

    fun onPromotionSelected(pieceType: PieceType) {
        val pending = _uiState.value.pendingPromotion ?: return
        _uiState.update { it.copy(pendingPromotion = null) }
        executeMove(pending.from, pending.to, pieceType)
    }

    fun dismissPromotion() {
        _uiState.update {
            it.copy(
                pendingPromotion = null,
                selectedSquare = null,
                legalMovesForSelected = emptyList()
            )
        }
    }

    private fun executeMove(from: Square, to: Square, promotion: PieceType?) {
        val state = _uiState.value
        val legalMove = ChessEngine.findMatchingLegalMove(state.position, from, to, promotion) ?: return
        val nextPos = ChessEngine.applyMoveUnchecked(state.position, legalMove)
        val nextFen = ChessEngine.toFen(nextPos)
        val san = ChessEngine.computeSan(state.position, legalMove, nextPos)
        val uci = legalMove.toUci()

        val nextUciList = state.uciHistory + uci
        val nextSanList = state.sanHistory + san
        val nextFenList = state.fenHistory + nextFen

        val repKeys = computeRepetitionKeys(nextFenList)
        val nextStatus = ChessEngine.evaluateStatus(nextPos, repKeys)

        viewModelScope.launch {
            val autoFlip = prefsRepo.preferences.first().autoFlipOfflineBoard
            _uiState.update {
                it.copy(
                    position = nextPos,
                    uciHistory = nextUciList,
                    sanHistory = nextSanList,
                    fenHistory = nextFenList,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    pendingPromotion = null,
                    lastMoveFrom = from,
                    lastMoveTo = to,
                    status = nextStatus,
                    isBoardFlipped = if (autoFlip) nextPos.turn == PieceColor.BLACK else it.isBoardFlipped,
                    showGameOverDialog = nextStatus is GameStatus.Finished
                )
            }

            when {
                nextStatus is GameStatus.Finished -> soundManager.play(ChessSoundEffect.GAME_OVER)
                nextStatus is GameStatus.Ongoing && nextStatus.inCheck -> soundManager.play(ChessSoundEffect.CHECK)
                legalMove.isCastleKingSide || legalMove.isCastleQueenSide -> soundManager.play(ChessSoundEffect.CASTLE)
                legalMove.isCapture -> soundManager.play(ChessSoundEffect.CAPTURE)
                else -> soundManager.play(ChessSoundEffect.MOVE)
            }

            persistState()
            if (nextStatus is GameStatus.Finished) {
                archiveFinishedGameIfNeeded()
            }
        }
    }

    fun undoMove() {
        val state = _uiState.value
        if (state.uciHistory.isEmpty()) return
        val finished = state.status as? GameStatus.Finished
        if (finished?.termination == GameTermination.TIMEOUT) return

        val prevUci = state.uciHistory.dropLast(1)
        val prevSan = state.sanHistory.dropLast(1)
        val prevFenList = if (state.fenHistory.size > 1) state.fenHistory.dropLast(1) else listOf(ChessEngine.INITIAL_FEN)
        val restoredFen = prevFenList.last()
        val restoredPos = ChessEngine.fromFen(restoredFen)
        val lastUci = prevUci.lastOrNull()
        val lastFrom = lastUci?.take(2)?.let { Square.fromAlgebraic(it) }
        val lastTo = lastUci?.drop(2)?.take(2)?.let { Square.fromAlgebraic(it) }
        val repKeys = computeRepetitionKeys(prevFenList)
        val nextStatus = ChessEngine.evaluateStatus(restoredPos, repKeys)
        historySavedForCurrentGame = false

        viewModelScope.launch {
            val autoFlip = prefsRepo.preferences.first().autoFlipOfflineBoard
            _uiState.update {
                it.copy(
                    position = restoredPos,
                    uciHistory = prevUci,
                    sanHistory = prevSan,
                    fenHistory = prevFenList,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    pendingPromotion = null,
                    lastMoveFrom = lastFrom,
                    lastMoveTo = lastTo,
                    status = nextStatus,
                    isBoardFlipped = if (autoFlip) restoredPos.turn == PieceColor.BLACK else it.isBoardFlipped,
                    showGameOverDialog = false
                )
            }
            persistState()
        }
    }

    fun flipBoard() {
        _uiState.update { it.copy(isBoardFlipped = !it.isBoardFlipped) }
    }

    fun restartCurrentGame() {
        val state = _uiState.value
        viewModelScope.launch {
            startNewGameInternal(
                timerMinutes = state.timerMinutes,
                whiteName = state.whiteName,
                blackName = state.blackName
            )
        }
    }

    fun startNewGame(timerMinutes: Int, whiteName: String, blackName: String) {
        viewModelScope.launch {
            prefsRepo.setDefaultTimerMinutes(timerMinutes)
            prefsRepo.setPlayerNames(whiteName, blackName)
            startNewGameInternal(
                timerMinutes = timerMinutes,
                whiteName = whiteName.trim().ifBlank { "Player 1 (White)" },
                blackName = blackName.trim().ifBlank { "Player 2 (Black)" }
            )
        }
    }

    fun setShowNewGameDialog(show: Boolean) {
        _uiState.update { it.copy(showNewGameDialog = show) }
    }

    fun dismissGameOverDialog() {
        _uiState.update { it.copy(showGameOverDialog = false) }
    }

    fun resignActivePlayer() {
        val state = _uiState.value
        if (state.status !is GameStatus.Ongoing) return
        val winner = state.position.turn.opposite()
        _uiState.update {
            it.copy(
                status = GameStatus.Finished(winner, GameTermination.RESIGNATION),
                showGameOverDialog = true
            )
        }
        soundManager.play(ChessSoundEffect.GAME_OVER)
        viewModelScope.launch {
            persistState()
            archiveFinishedGameIfNeeded()
        }
    }

    fun declareMutualDraw() {
        val state = _uiState.value
        if (state.status !is GameStatus.Ongoing) return
        _uiState.update {
            it.copy(
                status = GameStatus.Finished(null, GameTermination.DRAW_AGREEMENT),
                showGameOverDialog = true
            )
        }
        soundManager.play(ChessSoundEffect.GAME_OVER)
        viewModelScope.launch {
            persistState()
            archiveFinishedGameIfNeeded()
        }
    }

    private suspend fun startNewGameInternal(
        timerMinutes: Int,
        whiteName: String,
        blackName: String
    ) {
        val initialMs = timerMinutes * 60_000L
        historySavedForCurrentGame = false
        _uiState.value = OfflineUiState(
            isInitialized = true,
            position = ChessEngine.initialPosition(),
            uciHistory = emptyList(),
            sanHistory = emptyList(),
            fenHistory = listOf(ChessEngine.INITIAL_FEN),
            whiteName = whiteName,
            blackName = blackName,
            timerMinutes = timerMinutes,
            whiteTimeMs = initialMs,
            blackTimeMs = initialMs,
            status = GameStatus.Ongoing(inCheck = false),
            isBoardFlipped = false,
            showGameOverDialog = false,
            showNewGameDialog = false
        )
        persistState()
    }

    private suspend fun persistState() {
        val state = _uiState.value
        if (!state.isInitialized) return
        val statusStr = if (state.status is GameStatus.Finished) "FINISHED" else "ONGOING"
        val winnerStr = when (val s = state.status) {
            is GameStatus.Finished -> s.winner?.name ?: "DRAW"
            else -> ""
        }
        val termStr = when (val s = state.status) {
            is GameStatus.Finished -> s.termination.name
            else -> ""
        }
        chessDao.saveOfflineGame(
            OfflineGameEntity(
                id = OfflineGameEntity.CURRENT_GAME_ID,
                fen = ChessEngine.toFen(state.position),
                uciHistoryJson = encodeStringList(state.uciHistory),
                sanHistoryJson = encodeStringList(state.sanHistory),
                fenHistoryJson = encodeStringList(state.fenHistory),
                whiteName = state.whiteName,
                blackName = state.blackName,
                timerMinutes = state.timerMinutes,
                whiteTimeMs = state.whiteTimeMs,
                blackTimeMs = state.blackTimeMs,
                status = statusStr,
                winner = winnerStr,
                termination = termStr,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun archiveFinishedGameIfNeeded() {
        if (historySavedForCurrentGame) return
        val state = _uiState.value
        val finished = state.status as? GameStatus.Finished ?: return
        historySavedForCurrentGame = true

        val resultString = when (finished.winner) {
            PieceColor.WHITE -> "1-0"
            PieceColor.BLACK -> "0-1"
            null -> "1/2-1/2"
        }
        val pgn = ChessEngine.toPgn(
            whiteName = state.whiteName,
            blackName = state.blackName,
            resultString = resultString,
            sanMoves = state.sanHistory
        )
        chessDao.insertGameHistory(
            GameHistoryEntity(
                mode = "OFFLINE",
                whiteName = state.whiteName,
                blackName = state.blackName,
                result = resultString,
                winner = finished.winner?.name ?: "DRAW",
                termination = finished.termination.label,
                pgn = pgn,
                uciHistoryJson = encodeStringList(state.uciHistory),
                sanHistoryJson = encodeStringList(state.sanHistory),
                finalFen = ChessEngine.toFen(state.position),
                moveCount = state.sanHistory.size,
                timerMinutes = state.timerMinutes
            )
        )
    }

    private fun computeRepetitionKeys(fenHistory: List<String>): List<String> =
        fenHistory.mapNotNull { fen ->
            runCatching { ChessEngine.repetitionKey(ChessEngine.fromFen(fen)) }.getOrNull()
        }

    private fun encodeStringList(items: List<String>): String =
        JSONArray(items).toString()

    private fun decodeStringList(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { idx -> arr.getString(idx) }
    }.getOrDefault(emptyList())

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
