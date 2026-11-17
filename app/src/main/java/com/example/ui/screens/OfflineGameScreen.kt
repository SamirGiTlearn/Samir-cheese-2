package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.GameStatus
import com.example.chess.engine.GameTermination
import com.example.chess.engine.PieceColor
import com.example.data.local.UserPreferences
import com.example.ui.components.ChessBoard
import com.example.ui.components.GameOverModal
import com.example.ui.components.MoveHistoryStrip
import com.example.ui.components.PawnPromotionDialog
import com.example.ui.components.PlayerClockBanner
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.viewmodel.OfflineGameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineGameScreen(
    viewModel: OfflineGameViewModel,
    preferences: UserPreferences,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val captured = remember(state.position) {
        ChessEngine.computeCapturedSummary(state.position)
    }
    var showRestartConfirm by remember { mutableStateOf(false) }

    val ongoingCheck = (state.status as? GameStatus.Ongoing)?.inCheck == true
    val isGameFinished = state.status is GameStatus.Finished

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Offline Two-Player",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val subtitle = when (val s = state.status) {
                            is GameStatus.Finished -> {
                                val winLabel = when (s.winner) {
                                    PieceColor.WHITE -> "${state.whiteName} Won"
                                    PieceColor.BLACK -> "${state.blackName} Won"
                                    null -> "Draw"
                                }
                                "$winLabel • ${s.termination.label}"
                            }
                            is GameStatus.Ongoing -> {
                                val turnName = if (state.position.turn == PieceColor.WHITE) "White's Turn" else "Black's Turn"
                                when {
                                    state.isPaused -> "$turnName • Clock Paused"
                                    s.inCheck -> "$turnName • CHECK!"
                                    else -> turnName
                                }
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ongoingCheck) CrimsonCheck else Gold400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("offline_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isGameFinished) {
                        IconButton(
                            onClick = { viewModel.togglePause() },
                            modifier = Modifier.testTag("offline_pause_clock_btn")
                        ) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (state.isPaused) "Resume clock" else "Pause clock"
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.flipBoard() },
                        modifier = Modifier.testTag("offline_flip_btn")
                    ) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = "Flip board")
                    }
                    IconButton(
                        onClick = { viewModel.setShowNewGameDialog(true) },
                        modifier = Modifier.testTag("offline_new_game_top_btn")
                    ) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "New game setup")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val topColor = if (state.isBoardFlipped) PieceColor.WHITE else PieceColor.BLACK
                val bottomColor = topColor.opposite()

                // Top Player Banner
                PlayerClockBanner(
                    playerName = if (topColor == PieceColor.WHITE) state.whiteName else state.blackName,
                    pieceColor = topColor,
                    timeRemainingMs = if (topColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                    isActiveTurn = !isGameFinished && state.position.turn == topColor,
                    isInCheck = ongoingCheck && state.position.turn == topColor,
                    capturedPieces = if (topColor == PieceColor.WHITE) captured.capturedByWhite else captured.capturedByBlack,
                    materialAdvantage = if (topColor == PieceColor.WHITE) captured.whiteAdvantage.coerceAtLeast(0)
                    else (-captured.whiteAdvantage).coerceAtLeast(0),
                    isPaused = state.isPaused
                )

                // 8x8 Chessboard (always displays "Made by Samir" at bottom center)
                ChessBoard(
                    position = state.position,
                    boardTheme = preferences.boardTheme,
                    selectedSquare = state.selectedSquare,
                    legalMovesForSelected = state.legalMovesForSelected,
                    lastMoveFrom = state.lastMoveFrom,
                    lastMoveTo = state.lastMoveTo,
                    isFlipped = state.isBoardFlipped,
                    showLegalMoves = preferences.showLegalMoves,
                    interactionEnabled = !isGameFinished,
                    onSquareTapped = viewModel::onSquareTapped
                )

                // Bottom Player Banner
                PlayerClockBanner(
                    playerName = if (bottomColor == PieceColor.WHITE) state.whiteName else state.blackName,
                    pieceColor = bottomColor,
                    timeRemainingMs = if (bottomColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                    isActiveTurn = !isGameFinished && state.position.turn == bottomColor,
                    isInCheck = ongoingCheck && state.position.turn == bottomColor,
                    capturedPieces = if (bottomColor == PieceColor.WHITE) captured.capturedByWhite else captured.capturedByBlack,
                    materialAdvantage = if (bottomColor == PieceColor.WHITE) captured.whiteAdvantage.coerceAtLeast(0)
                    else (-captured.whiteAdvantage).coerceAtLeast(0),
                    isPaused = state.isPaused
                )

                // Move History Strip
                MoveHistoryStrip(sanHistory = state.sanHistory)

                // Offline Game Action Bar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val canUndo = state.uciHistory.isNotEmpty() &&
                            (state.status as? GameStatus.Finished)?.termination != GameTermination.TIMEOUT
                        OfflineControlButton(
                            icon = Icons.AutoMirrored.Rounded.Undo,
                            label = "Undo",
                            enabled = canUndo,
                            onClick = { viewModel.undoMove() },
                            modifier = Modifier.testTag("offline_undo_btn")
                        )
                        OfflineControlButton(
                            icon = Icons.Rounded.Refresh,
                            label = "Restart",
                            enabled = true,
                            onClick = {
                                if (state.uciHistory.isEmpty()) viewModel.restartCurrentGame()
                                else showRestartConfirm = true
                            },
                            modifier = Modifier.testTag("offline_restart_btn")
                        )
                        OfflineControlButton(
                            icon = Icons.Rounded.AddCircleOutline,
                            label = "New",
                            enabled = true,
                            onClick = { viewModel.setShowNewGameDialog(true) },
                            modifier = Modifier.testTag("offline_new_btn")
                        )
                        OfflineControlButton(
                            icon = Icons.Rounded.Handshake,
                            label = "Draw",
                            enabled = !isGameFinished,
                            onClick = { viewModel.declareMutualDraw() },
                            modifier = Modifier.testTag("offline_draw_btn")
                        )
                        OfflineControlButton(
                            icon = Icons.Rounded.Flag,
                            label = "Resign",
                            enabled = !isGameFinished,
                            onClick = { viewModel.resignActivePlayer() },
                            modifier = Modifier.testTag("offline_resign_btn")
                        )
                    }
                }
            }
        }
    }

    state.pendingPromotion?.let { promo ->
        PawnPromotionDialog(
            color = promo.color,
            onSelect = viewModel::onPromotionSelected,
            onDismiss = viewModel::dismissPromotion
        )
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Restart Match?") },
            text = { Text("Reset the board and clocks for the current players?") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartConfirm = false
                        viewModel.restartCurrentGame()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
                ) { Text("Restart") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (state.showNewGameDialog) {
        NewOfflineGameDialog(
            initialTimerMinutes = state.timerMinutes,
            initialWhiteName = state.whiteName,
            initialBlackName = state.blackName,
            onConfirm = { mins, wName, bName ->
                viewModel.startNewGame(mins, wName, bName)
            },
            onDismiss = { viewModel.setShowNewGameDialog(false) }
        )
    }

    val finished = state.status as? GameStatus.Finished
    if (finished != null && state.showGameOverDialog) {
        val title = when (finished.winner) {
            PieceColor.WHITE -> "${state.whiteName} Wins!"
            PieceColor.BLACK -> "${state.blackName} Wins!"
            null -> "Match Drawn"
        }
        val subtitle = if (finished.termination == GameTermination.TIMEOUT) {
            val loserName = if (finished.winner == PieceColor.WHITE) state.blackName else state.whiteName
            "$loserName ran out of time after ${state.sanHistory.size} moves."
        } else {
            "Ended by ${finished.termination.label} after ${state.sanHistory.size} moves."
        }
        GameOverModal(
            title = title,
            subtitle = subtitle,
            isDraw = finished.winner == null,
            primaryActionLabel = "Rematch",
            onPrimaryAction = { viewModel.restartCurrentGame() },
            secondaryActionLabel = "Inspect Board",
            onSecondaryAction = { viewModel.dismissGameOverDialog() },
            onDismiss = { viewModel.dismissGameOverDialog() }
        )
    }
}

@Composable
private fun OfflineControlButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun NewOfflineGameDialog(
    initialTimerMinutes: Int,
    initialWhiteName: String,
    initialBlackName: String,
    onConfirm: (Int, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(initialTimerMinutes) }
    var whiteName by remember { mutableStateOf(initialWhiteName) }
    var blackName by remember { mutableStateOf(initialBlackName) }
    val timerOptions = listOf(1, 3, 5, 10, 15)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Offline Match",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = whiteName,
                    onValueChange = { whiteName = it },
                    label = { Text("Player 1 (White)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = blackName,
                    onValueChange = { blackName = it },
                    label = { Text("Player 2 (Black)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Game Timer (Minutes per Player)",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timerOptions.forEach { mins ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text("${mins}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMinutes, whiteName, blackName) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
            ) {
                Text("Start Match", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
