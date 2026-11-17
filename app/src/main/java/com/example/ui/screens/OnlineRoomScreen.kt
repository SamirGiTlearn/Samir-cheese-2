package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.GameTermination
import com.example.chess.engine.PieceColor
import com.example.chess.engine.Square
import com.example.data.local.UserPreferences
import com.example.data.remote.OnlineChessRepository
import com.example.data.remote.OnlineRoom
import com.example.ui.components.ChessBoard
import com.example.ui.components.GameOverModal
import com.example.ui.components.MoveHistoryStrip
import com.example.ui.components.PawnPromotionDialog
import com.example.ui.components.PlayerClockBanner
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.OnlineViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineRoomScreen(
    roomCode: String,
    onlineViewModel: OnlineViewModel,
    preferences: UserPreferences,
    onNavigateToRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by onlineViewModel.matchState.collectAsStateWithLifecycle()
    var showResignConfirm by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(roomCode) {
        onlineViewModel.startObservingRoom(roomCode)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    DisposableEffect(roomCode) {
        onDispose {
            onlineViewModel.stopObservingRoom()
        }
    }

    val room = state.room

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Online Room ${room?.roomCode ?: roomCode}",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val statusSubtitle = when {
                            room == null -> "Connecting..."
                            room.status == "WAITING" -> "Waiting for friend to join..."
                            room.status == "FINISHED" -> "Match Finished"
                            room.turn == room.playerColorFor(onlineViewModel.currentUserId) ->
                                if (state.isInCheck) "Your Turn • CHECK!" else "Your Turn"
                            else -> "Opponent's Turn"
                        }
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isInCheck) CrimsonCheck else Gold400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("online_room_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
            if (room == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage!!,
                            color = CrimsonCheck,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(onClick = onBack) { Text("Back to Lobby") }
                    } else {
                        CircularProgressIndicator(color = Gold400)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Connecting to room $roomCode...")
                    }
                }
            } else if (room.status == "WAITING") {
                WaitingRoomContent(
                    room = room,
                    isHost = room.hostId == onlineViewModel.currentUserId,
                    onCopyCode = { copyText(context, "Room Code", room.roomCode) },
                    onCopyLink = {
                        copyText(context, "Invitation Link", OnlineChessRepository.buildInvitationLink(room.roomCode))
                    },
                    onInviteFriend = { shareRoomInvite(context, room.roomCode) },
                    onCancelRoom = {
                        onlineViewModel.cancelWaitingRoom(room.roomCode, onBack)
                    }
                )
            } else {
                val myColorStr = room.playerColorFor(onlineViewModel.currentUserId) ?: "WHITE"
                val myColor = PieceColor.fromString(myColorStr)
                val oppColor = myColor.opposite()
                val isFlipped = myColor == PieceColor.BLACK

                val captured = remember(state.position) {
                    ChessEngine.computeCapturedSummary(state.position)
                }
                val lastFrom = remember(room.lastMoveUci) {
                    if (room.lastMoveUci.length >= 4) Square.fromAlgebraic(room.lastMoveUci.take(2)) else null
                }
                val lastTo = remember(room.lastMoveUci) {
                    if (room.lastMoveUci.length >= 4) Square.fromAlgebraic(room.lastMoveUci.drop(2).take(2)) else null
                }

                val isWhiteOnline = (nowMs - room.whiteLastSeenMs) < 35_000L
                val isBlackOnline = (nowMs - room.blackLastSeenMs) < 35_000L
                val isMyTurn = room.status == "ACTIVE" && room.turn == myColorStr

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 580.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.errorMessage != null) {
                        Surface(
                            color = CrimsonCheck.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = state.errorMessage!!,
                                    color = CrimsonCheck,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onlineViewModel.clearMatchError() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    // Incoming Draw Offer Banner
                    if (room.status == "ACTIVE" && room.drawOfferBy == oppColor.name) {
                        Surface(
                            color = Gold500.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Gold500, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Opponent offered a draw.",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Gold400,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onlineViewModel.declineDrawOffer() }) {
                                    Text("Decline")
                                }
                                Button(
                                    onClick = { onlineViewModel.acceptDrawOffer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
                                ) {
                                    Text("Accept")
                                }
                            }
                        }
                    }

                    // Rematch Ready Banner
                    if (room.status == "FINISHED" && room.rematchRoomCode.isNotBlank()) {
                        Surface(
                            color = EmeraldWin.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, EmeraldWin, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Rematch Room ${room.rematchRoomCode} is ready!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldWin,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        onlineViewModel.joinRematchRoom(room.rematchRoomCode, onNavigateToRoom)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldWin, contentColor = Color(0xFF0A140F))
                                ) {
                                    Text("Join Rematch", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Opponent Banner (Top)
                    PlayerClockBanner(
                        playerName = if (oppColor == PieceColor.WHITE) room.whiteName.ifBlank { "White" }
                        else room.blackName.ifBlank { "Black" },
                        pieceColor = oppColor,
                        timeRemainingMs = if (oppColor == PieceColor.WHITE) state.localWhiteTimeMs else state.localBlackTimeMs,
                        isActiveTurn = room.status == "ACTIVE" && room.turn == oppColor.name,
                        isInCheck = state.isInCheck && room.turn == oppColor.name,
                        capturedPieces = if (oppColor == PieceColor.WHITE) captured.capturedByWhite else captured.capturedByBlack,
                        materialAdvantage = if (oppColor == PieceColor.WHITE) captured.whiteAdvantage.coerceAtLeast(0)
                        else (-captured.whiteAdvantage).coerceAtLeast(0),
                        photoUrl = if (oppColor == PieceColor.WHITE) room.whitePhotoUrl else room.blackPhotoUrl,
                        connectionStatus = if ((oppColor == PieceColor.WHITE && isWhiteOnline) ||
                            (oppColor == PieceColor.BLACK && isBlackOnline)
                        ) "ONLINE" else "RECONNECTING"
                    )

                    // Synchronized 8x8 Chessboard (always displays "Made by Samir" at bottom center)
                    ChessBoard(
                        position = state.position,
                        boardTheme = preferences.boardTheme,
                        selectedSquare = state.selectedSquare,
                        legalMovesForSelected = state.legalMovesForSelected,
                        lastMoveFrom = lastFrom,
                        lastMoveTo = lastTo,
                        isFlipped = isFlipped,
                        showLegalMoves = preferences.showLegalMoves,
                        interactionEnabled = isMyTurn && !state.isSubmittingMove,
                        onSquareTapped = onlineViewModel::onSquareTapped
                    )

                    // Local Player Banner (Bottom)
                    PlayerClockBanner(
                        playerName = if (myColor == PieceColor.WHITE) room.whiteName.ifBlank { "You (White)" }
                        else room.blackName.ifBlank { "You (Black)" },
                        pieceColor = myColor,
                        timeRemainingMs = if (myColor == PieceColor.WHITE) state.localWhiteTimeMs else state.localBlackTimeMs,
                        isActiveTurn = isMyTurn,
                        isInCheck = state.isInCheck && room.turn == myColor.name,
                        capturedPieces = if (myColor == PieceColor.WHITE) captured.capturedByWhite else captured.capturedByBlack,
                        materialAdvantage = if (myColor == PieceColor.WHITE) captured.whiteAdvantage.coerceAtLeast(0)
                        else (-captured.whiteAdvantage).coerceAtLeast(0),
                        photoUrl = if (myColor == PieceColor.WHITE) room.whitePhotoUrl else room.blackPhotoUrl,
                        connectionStatus = "ONLINE"
                    )

                    MoveHistoryStrip(sanHistory = room.sanHistory)

                    // Match Controls
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (room.status == "ACTIVE") {
                                TextButton(
                                    onClick = { onlineViewModel.offerDraw() },
                                    enabled = room.drawOfferBy != myColorStr,
                                    modifier = Modifier.testTag("online_offer_draw_btn")
                                ) {
                                    Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (room.drawOfferBy == myColorStr) "Draw Offered" else "Offer Draw")
                                }

                                TextButton(
                                    onClick = { showResignConfirm = true },
                                    modifier = Modifier.testTag("online_resign_btn")
                                ) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = CrimsonCheck, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resign", color = CrimsonCheck)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (room.rematchRoomCode.isNotBlank()) {
                                            onlineViewModel.joinRematchRoom(room.rematchRoomCode, onNavigateToRoom)
                                        } else {
                                            onlineViewModel.requestRematch(onNavigateToRoom)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F)),
                                    modifier = Modifier.testTag("online_rematch_btn")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (room.rematchRoomCode.isNotBlank()) "Join Rematch" else "Rematch",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingPromotion?.let { promo ->
        PawnPromotionDialog(
            color = promo.color,
            onSelect = onlineViewModel::onPromotionSelected,
            onDismiss = onlineViewModel::dismissPromotion
        )
    }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text("Resign Online Match?") },
            text = { Text("This will immediately award the win to your opponent.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResignConfirm = false
                        onlineViewModel.resignMatch()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonCheck)
                ) { Text("Resign") }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (room != null && room.status == "FINISHED" && state.showGameOverDialog) {
        val myColorStr = room.playerColorFor(onlineViewModel.currentUserId)
        val termLabel = GameTermination.entries.firstOrNull { it.name == room.termination }?.label
            ?: room.termination
        val title = when {
            room.winner == "DRAW" -> "Match Drawn"
            room.winner == myColorStr -> "Victory!"
            else -> "Defeat"
        }
        val subtitle = if (room.termination == GameTermination.TIMEOUT.name) {
            val timedOutName = if (room.winner == "WHITE") {
                room.blackName.ifBlank { "Black" }
            } else {
                room.whiteName.ifBlank { "White" }
            }
            "$timedOutName ran out of time after ${room.moveCount} moves."
        } else {
            "Ended by $termLabel after ${room.moveCount} moves."
        }
        GameOverModal(
            title = title,
            subtitle = subtitle,
            isDraw = room.winner == "DRAW",
            primaryActionLabel = if (room.rematchRoomCode.isNotBlank()) "Join Rematch" else "Request Rematch",
            onPrimaryAction = {
                if (room.rematchRoomCode.isNotBlank()) {
                    onlineViewModel.joinRematchRoom(room.rematchRoomCode, onNavigateToRoom)
                } else {
                    onlineViewModel.requestRematch(onNavigateToRoom)
                }
            },
            secondaryActionLabel = "Inspect Board",
            onSecondaryAction = { onlineViewModel.dismissGameOverDialog() },
            onDismiss = { onlineViewModel.dismissGameOverDialog() }
        )
    }
}

@Composable
private fun WaitingRoomContent(
    room: OnlineRoom,
    isHost: Boolean,
    onCopyCode: () -> Unit,
    onCopyLink: () -> Unit,
    onInviteFriend: () -> Unit,
    onCancelRoom: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Gold500.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = Gold400, strokeWidth = 3.dp)
                Text(
                    text = "Waiting for Opponent",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Gold400
                )
                Text(
                    text = "Share this Room Code or Invitation Link via WhatsApp, Messenger, or Telegram. Your game begins automatically once your friend joins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Gold500.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PRIVATE ROOM CODE", style = MaterialTheme.typography.labelSmall, color = Gold400)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = room.roomCode,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = 5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("waiting_room_code_text")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You play ${room.creatorColor.lowercase().replaceFirstChar { it.uppercase() }} • ${room.timerMinutes} min clock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onInviteFriend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("invite_friend_share_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite Friend (WhatsApp / Telegram)", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyCode,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("copy_room_code_btn")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Code")
                    }
                    OutlinedButton(
                        onClick = onCopyLink,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("copy_invite_link_btn")
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Link")
                    }
                }

                if (isHost) {
                    TextButton(
                        onClick = onCancelRoom,
                        modifier = Modifier.testTag("cancel_waiting_room_btn")
                    ) {
                        Text("Cancel & Close Room", color = CrimsonCheck)
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun shareRoomInvite(context: Context, roomCode: String) {
    val webLink = OnlineChessRepository.buildInvitationLink(roomCode)
    val deepLink = OnlineChessRepository.buildDeepLinkUri(roomCode)
    val message = buildString {
        appendLine("Play Chess with me in Chess Master – Made by Samir!")
        appendLine("Room Code: $roomCode")
        appendLine("Join Link: $webLink")
        append("Direct App Link: $deepLink")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Chess Master Invitation ($roomCode)")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Invite Friend to Room $roomCode"))
}
