package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.ChessMove
import com.example.chess.engine.ChessPosition
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.chess.engine.Square
import com.example.ui.theme.BoardTheme
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500

data class PendingPromotion(
    val from: Square,
    val to: Square,
    val color: PieceColor
)

/**
 * Interactive 8x8 luxury chessboard with smooth piece movement animation,
 * legal move highlights, check warnings, last-move highlights, coordinates,
 * and the MANDATORY permanent "Made by Samir" watermark at the bottom center.
 */
@Composable
fun ChessBoard(
    position: ChessPosition,
    boardTheme: BoardTheme,
    selectedSquare: Square?,
    legalMovesForSelected: List<ChessMove>,
    lastMoveFrom: Square?,
    lastMoveTo: Square?,
    isFlipped: Boolean = false,
    showLegalMoves: Boolean = true,
    interactionEnabled: Boolean = true,
    onSquareTapped: (Square) -> Unit,
    modifier: Modifier = Modifier
) {
    val checkedKingSquare = remember(position) {
        if (ChessEngine.isInCheck(position, position.turn)) {
            ChessEngine.findKing(position, position.turn)
        } else null
    }

    val legalTargetMap = remember(legalMovesForSelected) {
        legalMovesForSelected.associateBy { it.to }
    }

    // Smoothly animate the last moved piece from its origin square to destination square
    val moveAnim = remember { Animatable(1f) }
    LaunchedEffect(lastMoveFrom, lastMoveTo, position.fullmoveNumber, position.turn) {
        if (lastMoveFrom != null && lastMoveTo != null) {
            moveAnim.snapTo(0f)
            moveAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
            )
        } else {
            moveAnim.snapTo(1f)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Gold500.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
        color = boardTheme.frameColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Gold500.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .testTag("chessboard_grid")
            ) {
                val boardPx = constraints.maxWidth.toFloat()
                val sqPx = boardPx / 8f

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isFlipped, interactionEnabled, position) {
                            detectTapGestures { offset ->
                                if (!interactionEnabled) return@detectTapGestures
                                val col = (offset.x / sqPx).toInt().coerceIn(0, 7)
                                val row = (offset.y / sqPx).toInt().coerceIn(0, 7)
                                val file = if (isFlipped) 7 - col else col
                                val rank = if (isFlipped) row else 7 - row
                                onSquareTapped(Square(file, rank))
                            }
                        }
                ) {
                    val sqSize = size.width / 8f

                    // 1. Draw 64 squares & highlights
                    for (rank in 0..7) {
                        for (file in 0..7) {
                            val sq = Square(file, rank)
                            val col = if (isFlipped) 7 - file else file
                            val row = if (isFlipped) rank else 7 - rank
                            val topLeft = Offset(col * sqSize, row * sqSize)

                            val baseColor = if (sq.isLightSquare()) boardTheme.lightSquare else boardTheme.darkSquare
                            drawRect(
                                color = baseColor,
                                topLeft = topLeft,
                                size = Size(sqSize, sqSize)
                            )

                            // Last move highlight
                            if (sq == lastMoveFrom || sq == lastMoveTo) {
                                drawRect(
                                    color = boardTheme.lastMoveHighlight,
                                    topLeft = topLeft,
                                    size = Size(sqSize, sqSize)
                                )
                            }

                            // Selected square highlight
                            if (sq == selectedSquare) {
                                drawRect(
                                    color = boardTheme.selectedHighlight,
                                    topLeft = topLeft,
                                    size = Size(sqSize, sqSize)
                                )
                            }

                            // King in Check warning radial glow
                            if (sq == checkedKingSquare) {
                                val center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            CrimsonCheck.copy(alpha = 0.85f),
                                            CrimsonCheck.copy(alpha = 0.35f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = sqSize * 0.55f
                                    ),
                                    center = center,
                                    radius = sqSize * 0.55f
                                )
                            }
                        }
                    }

                    // 2. Draw pieces (animating the piece that just landed at lastMoveTo)
                    for (rank in 0..7) {
                        for (file in 0..7) {
                            val sq = Square(file, rank)
                            val piece = position.pieceAt(sq) ?: continue

                            val col = if (isFlipped) 7 - file else file
                            val row = if (isFlipped) rank else 7 - rank
                            var topLeft = Offset(col * sqSize, row * sqSize)

                            if (sq == lastMoveTo && lastMoveFrom != null && moveAnim.value < 1f) {
                                val fromCol = if (isFlipped) 7 - lastMoveFrom.file else lastMoveFrom.file
                                val fromRow = if (isFlipped) lastMoveFrom.rank else 7 - lastMoveFrom.rank
                                val startOffset = Offset(fromCol * sqSize, fromRow * sqSize)
                                val t = moveAnim.value
                                topLeft = Offset(
                                    x = startOffset.x + (topLeft.x - startOffset.x) * t,
                                    y = startOffset.y + (topLeft.y - startOffset.y) * t
                                )
                            }

                            drawChessPiece(
                                piece = piece,
                                topLeft = topLeft,
                                squareSize = sqSize
                            )
                        }
                    }

                    // 3. Draw legal move dots / capture rings on top for crystal clarity
                    if (showLegalMoves && selectedSquare != null) {
                        for ((targetSq, move) in legalTargetMap) {
                            val col = if (isFlipped) 7 - targetSq.file else targetSq.file
                            val row = if (isFlipped) targetSq.rank else 7 - targetSq.rank
                            val center = Offset(col * sqSize + sqSize / 2f, row * sqSize + sqSize / 2f)
                            val hasPiece = position.pieceAt(targetSq) != null || move.isEnPassant
                            if (hasPiece) {
                                drawCircle(
                                    color = Color(0xCC221B14),
                                    radius = sqSize * 0.43f,
                                    center = center,
                                    style = Stroke(width = sqSize * 0.085f)
                                )
                                drawCircle(
                                    color = Gold400.copy(alpha = 0.75f),
                                    radius = sqSize * 0.43f,
                                    center = center,
                                    style = Stroke(width = sqSize * 0.035f)
                                )
                            } else {
                                drawCircle(
                                    color = Color(0x881A1612),
                                    radius = sqSize * 0.16f,
                                    center = center
                                )
                            }
                        }
                    }
                }

                // Subtle rank numbers (left column) & file letters (bottom row) inside board corners
                BoardCoordinatesOverlay(
                    isFlipped = isFlipped,
                    boardTheme = boardTheme
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // MANDATORY PERMANENT WATERMARK ON EVERY CHESSBOARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .testTag("chessboard_watermark_samir")
                    .semantics { contentDescription = "Made by Samir" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Made by Samir",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 2.2.sp,
                    color = Gold400.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BoardCoordinatesOverlay(
    isFlipped: Boolean,
    boardTheme: BoardTheme
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Rank labels on left edge
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            for (row in 0..7) {
                val rankLabel = if (isFlipped) (row + 1).toString() else (8 - row).toString()
                val isLight = if (isFlipped) row % 2 != 0 else row % 2 == 0
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = rankLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) boardTheme.darkSquare.copy(alpha = 0.85f)
                        else boardTheme.lightSquare.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // File labels on bottom edge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (col in 0..7) {
                val fileChar = if (isFlipped) ('h' - col) else ('a' + col)
                val isLight = if (isFlipped) col % 2 == 0 else col % 2 != 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 2.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Text(
                        text = fileChar.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) boardTheme.darkSquare.copy(alpha = 0.85f)
                        else boardTheme.lightSquare.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun PawnPromotionDialog(
    color: PieceColor,
    onSelect: (PieceType) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Promote Pawn",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                options.forEach { pieceType ->
                    Surface(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .clickable { onSelect(pieceType) }
                            .testTag("promote_${pieceType.name.lowercase()}"),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ChessPieceGraphic(
                                piece = com.example.chess.engine.Piece(pieceType, color),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
