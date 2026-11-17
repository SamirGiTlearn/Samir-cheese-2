package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.JetBrainsMonoFontFamily

fun formatClockMillis(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    if (safeMs == 0L) return "00:00"
    return if (safeMs < 10_000L) {
        val floorSeconds = safeMs / 1000L
        val minutes = floorSeconds / 60L
        val seconds = floorSeconds % 60L
        val tenths = (safeMs % 1000L) / 100L
        "%d:%02d.%d".format(minutes, seconds, tenths)
    } else {
        val totalSeconds = (safeMs + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        "%02d:%02d".format(minutes, seconds)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerClockBanner(
    playerName: String,
    pieceColor: PieceColor,
    timeRemainingMs: Long,
    isActiveTurn: Boolean,
    isInCheck: Boolean,
    capturedPieces: List<PieceType>,
    materialAdvantage: Int,
    photoUrl: String = "",
    connectionStatus: String? = null, // "ONLINE", "RECONNECTING", "OFFLINE"
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isTimedOut = timeRemainingMs <= 0L
    val borderColor by animateColorAsState(
        targetValue = when {
            isTimedOut || isInCheck -> CrimsonCheck
            isActiveTurn && !isPaused -> Gold500
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "playerBannerBorder"
    )
    val isLowTime = timeRemainingMs in 1..20_000L

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = if (isActiveTurn) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = if (isActiveTurn) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar or Piece Emblem
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (pieceColor == PieceColor.WHITE) Color(0xFFEDE1C5) else Color(0xFF231E18)
                        )
                        .border(1.dp, Gold500.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "$playerName avatar",
                            modifier = Modifier.size(38.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        MiniCapturedPieceIcon(
                            type = PieceType.KING,
                            color = pieceColor,
                            size = 26.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = playerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (connectionStatus != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val isOnline = connectionStatus == "ONLINE"
                            Icon(
                                imageVector = if (isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                                contentDescription = "Connection $connectionStatus",
                                tint = if (isOnline) EmeraldWin else CrimsonCheck,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (isInCheck) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CrimsonCheck.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "CHECK",
                                    color = CrimsonCheck,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (capturedPieces.isEmpty()) {
                            Text(
                                text = if (pieceColor == PieceColor.WHITE) "White" else "Black",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy((-3).dp),
                                maxLines = 1
                            ) {
                                capturedPieces.take(12).forEach { captured ->
                                    MiniCapturedPieceIcon(
                                        type = captured,
                                        color = pieceColor.opposite(),
                                        size = 14.dp
                                    )
                                }
                            }
                        }
                        if (materialAdvantage > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+$materialAdvantage",
                                style = MaterialTheme.typography.labelSmall,
                                color = Gold400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Digital Chess Clock
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    isTimedOut || (isLowTime && isActiveTurn && !isPaused) -> CrimsonCheck.copy(alpha = 0.22f)
                    isActiveTurn && !isPaused -> Gold500.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainerLowest
                },
                modifier = Modifier
                    .border(
                        1.dp,
                        when {
                            isTimedOut || (isLowTime && isActiveTurn && !isPaused) -> CrimsonCheck
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .testTag("player_clock_${pieceColor.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = when {
                            isTimedOut || (isLowTime && isActiveTurn && !isPaused) -> CrimsonCheck
                            isActiveTurn && !isPaused -> Gold400
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = formatClockMillis(timeRemainingMs),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = when {
                            isTimedOut || (isLowTime && isActiveTurn && !isPaused) -> CrimsonCheck
                            isActiveTurn && !isPaused -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isPaused && isActiveTurn && !isTimedOut) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "II",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Gold400
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoveHistoryStrip(
    sanHistory: List<String>,
    highlightedPlyIndex: Int = sanHistory.lastIndex,
    onPlyClicked: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sanHistory.size) {
        if (sanHistory.isNotEmpty()) {
            listState.animateScrollToItem((sanHistory.size - 1) / 2)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        if (sanHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Move history will appear here once White plays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val pairs = (sanHistory.indices step 2).map { idx ->
                Triple(
                    (idx / 2) + 1,
                    idx to sanHistory[idx],
                    if (idx + 1 < sanHistory.size) (idx + 1) to sanHistory[idx + 1] else null
                )
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pairs) { _, (moveNum, whitePair, blackPair) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$moveNum.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MoveChip(
                            text = whitePair.second,
                            selected = whitePair.first == highlightedPlyIndex,
                            onClick = onPlyClicked?.let { cb -> { cb(whitePair.first) } }
                        )
                        if (blackPair != null) {
                            MoveChip(
                                text = blackPair.second,
                                selected = blackPair.first == highlightedPlyIndex,
                                onClick = onPlyClicked?.let { cb -> { cb(blackPair.first) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveChip(
    text: String,
    selected: Boolean,
    onClick: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) Gold500.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Gold400 else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun GameOverModal(
    title: String,
    subtitle: String,
    isDraw: Boolean,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Gold500.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                .testTag("game_over_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Gold500.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDraw) Icons.Rounded.Handshake else Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Gold400
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("game_over_primary_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold500,
                        contentColor = Color(0xFF14130F)
                    )
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("game_over_secondary_btn")
                ) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}
