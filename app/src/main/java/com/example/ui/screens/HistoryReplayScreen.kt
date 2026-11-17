package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.Square
import com.example.data.local.GameHistoryEntity
import com.example.data.local.UserPreferences
import com.example.ui.components.ChessBoard
import com.example.ui.components.MoveHistoryStrip
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(
    historyList: List<GameHistoryEntity>,
    onSelectGameForReplay: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onBack: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Game History",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("history_back_btn")) {
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
            if (historyList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = Gold400.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Completed Games Yet",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Finish an offline or online match to archive its PGN and replay every move.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyList, key = { it.id }) { game ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .clickable { onSelectGameForReplay(game.id) }
                                .testTag("history_item_${game.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (game.mode == "ONLINE") EmeraldWin.copy(alpha = 0.16f)
                                            else Gold500.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = game.mode,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (game.mode == "ONLINE") EmeraldWin else Gold400,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = game.result,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = Gold400
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${game.moveCount} plies",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${game.whiteName} vs ${game.blackName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${game.termination} • ${dateFormat.format(Date(game.playedAtMs))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onSelectGameForReplay(game.id) }) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Replay game", tint = Gold400)
                                    }
                                    IconButton(onClick = { onDeleteGame(game.id) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete game",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameReplayScreen(
    game: GameHistoryEntity?,
    preferences: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val uciMoves = remember(game) {
        runCatching {
            val arr = JSONArray(game?.uciHistoryJson ?: "[]")
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }
    val sanMoves = remember(game) {
        runCatching {
            val arr = JSONArray(game?.sanHistoryJson ?: "[]")
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    val positions = remember(uciMoves) {
        val list = mutableListOf(ChessEngine.initialPosition())
        var cur = list.first()
        for (uci in uciMoves) {
            val next = ChessEngine.tryApplyUciMove(cur, uci)?.second ?: break
            list.add(next)
            cur = next
        }
        list
    }

    var plyIndex by remember(game?.id) { mutableIntStateOf(0) }
    var isAutoPlaying by remember { mutableStateOf(false) }
    var isFlipped by remember { mutableStateOf(false) }

    LaunchedEffect(isAutoPlaying, plyIndex, positions.size) {
        if (isAutoPlaying) {
            if (plyIndex >= positions.lastIndex) {
                isAutoPlaying = false
            } else {
                delay(850L)
                plyIndex = (plyIndex + 1).coerceAtMost(positions.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Game Replay",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (game != null) {
                            Text(
                                text = "${game.whiteName} vs ${game.blackName} (${game.result})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Gold400
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("replay_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isFlipped = !isFlipped }) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = "Flip board")
                    }
                    if (game != null) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Chess PGN", game.pgn))
                                Toast.makeText(context, "PGN copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy PGN")
                        }
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
            if (game == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Game not found.")
                }
            } else {
                val currentPos = positions.getOrElse(plyIndex) { positions.last() }
                val lastUci = if (plyIndex > 0) uciMoves.getOrNull(plyIndex - 1) else null
                val lastFrom = lastUci?.take(2)?.let { Square.fromAlgebraic(it) }
                val lastTo = lastUci?.drop(2)?.take(2)?.let { Square.fromAlgebraic(it) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 580.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ChessBoard(
                        position = currentPos,
                        boardTheme = preferences.boardTheme,
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        lastMoveFrom = lastFrom,
                        lastMoveTo = lastTo,
                        isFlipped = isFlipped,
                        showLegalMoves = false,
                        interactionEnabled = false,
                        onSquareTapped = {}
                    )

                    MoveHistoryStrip(
                        sanHistory = sanMoves,
                        highlightedPlyIndex = plyIndex - 1,
                        onPlyClicked = { idx ->
                            isAutoPlaying = false
                            plyIndex = (idx + 1).coerceIn(0, positions.lastIndex)
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isAutoPlaying = false
                                    plyIndex = 0
                                },
                                enabled = plyIndex > 0
                            ) {
                                Icon(Icons.Rounded.FastRewind, contentDescription = "First move")
                            }
                            IconButton(
                                onClick = {
                                    isAutoPlaying = false
                                    plyIndex = (plyIndex - 1).coerceAtLeast(0)
                                },
                                enabled = plyIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = "Previous move")
                            }
                            IconButton(
                                onClick = {
                                    if (plyIndex >= positions.lastIndex) plyIndex = 0
                                    isAutoPlaying = !isAutoPlaying
                                },
                                enabled = positions.size > 1
                            ) {
                                Icon(
                                    imageVector = if (isAutoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isAutoPlaying) "Pause replay" else "Play replay",
                                    tint = Gold400
                                )
                            }
                            IconButton(
                                onClick = {
                                    isAutoPlaying = false
                                    plyIndex = (plyIndex + 1).coerceAtMost(positions.lastIndex)
                                },
                                enabled = plyIndex < positions.lastIndex
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = "Next move")
                            }
                            IconButton(
                                onClick = {
                                    isAutoPlaying = false
                                    plyIndex = positions.lastIndex
                                },
                                enabled = plyIndex < positions.lastIndex
                            ) {
                                Icon(Icons.Rounded.FastForward, contentDescription = "Final move")
                            }
                        }
                    }

                    Text(
                        text = "Move $plyIndex of ${positions.lastIndex} • ${game.termination}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
