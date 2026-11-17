package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chess.engine.Piece
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType

/**
 * Custom high-detail vector Canvas Staunton chess pieces.
 * Ensures crisp, realistic 2D pieces across all Android screen densities without font glyph artifacts.
 */
@Composable
fun ChessPieceGraphic(
    piece: Piece,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawChessPiece(piece = piece, topLeft = Offset.Zero, squareSize = minOf(size.width, size.height))
    }
}

@Composable
fun MiniCapturedPieceIcon(
    type: PieceType,
    color: PieceColor,
    size: Dp = 16.dp
) {
    ChessPieceGraphic(
        piece = Piece(type, color),
        modifier = Modifier.size(size)
    )
}

fun DrawScope.drawChessPiece(
    piece: Piece,
    topLeft: Offset,
    squareSize: Float
) {
    val isWhite = piece.color == PieceColor.WHITE
    val fillBrush = if (isWhite) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFDF7), Color(0xFFEDE1C5), Color(0xFFD5BE90)),
            startY = topLeft.y,
            endY = topLeft.y + squareSize
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF433C33), Color(0xFF231E18), Color(0xFF120F0C)),
            startY = topLeft.y,
            endY = topLeft.y + squareSize
        )
    }
    val strokeColor = if (isWhite) Color(0xFF4A3722) else Color(0xFFD4AF37)
    val accentColor = if (isWhite) Color(0xFF8C6B43) else Color(0xFFE5C158)
    val strokeWidth = (squareSize * 0.036f).coerceAtLeast(1.2f)
    val shadowColor = Color.Black.copy(alpha = 0.28f)

    fun sx(fx: Float) = topLeft.x + squareSize * fx
    fun sy(fy: Float) = topLeft.y + squareSize * fy

    // Subtle ground contact shadow
    drawOval(
        color = shadowColor,
        topLeft = Offset(sx(0.18f), sy(0.83f)),
        size = Size(squareSize * 0.64f, squareSize * 0.10f)
    )

    when (piece.type) {
        PieceType.PAWN -> {
            val path = Path().apply {
                moveTo(sx(0.26f), sy(0.86f))
                lineTo(sx(0.74f), sy(0.86f))
                lineTo(sx(0.71f), sy(0.77f))
                lineTo(sx(0.60f), sy(0.70f))
                cubicTo(sx(0.58f), sy(0.56f), sx(0.57f), sy(0.49f), sx(0.56f), sy(0.43f))
                lineTo(sx(0.44f), sy(0.43f))
                cubicTo(sx(0.43f), sy(0.49f), sx(0.42f), sy(0.56f), sx(0.40f), sy(0.70f))
                lineTo(sx(0.29f), sy(0.77f))
                close()
            }
            drawPath(path, brush = fillBrush)
            drawPath(path, color = strokeColor, style = Stroke(strokeWidth, join = StrokeJoin.Round))

            // Collar ring
            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(sx(0.36f), sy(0.39f)),
                size = Size(squareSize * 0.28f, squareSize * 0.06f),
                cornerRadius = CornerRadius(squareSize * 0.03f)
            )
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(sx(0.36f), sy(0.39f)),
                size = Size(squareSize * 0.28f, squareSize * 0.06f),
                cornerRadius = CornerRadius(squareSize * 0.03f),
                style = Stroke(strokeWidth)
            )

            // Head
            val headCenter = Offset(sx(0.50f), sy(0.28f))
            val headRadius = squareSize * 0.135f
            drawCircle(brush = fillBrush, radius = headRadius, center = headCenter)
            drawCircle(color = strokeColor, radius = headRadius, center = headCenter, style = Stroke(strokeWidth))
        }

        PieceType.ROOK -> {
            val path = Path().apply {
                moveTo(sx(0.22f), sy(0.86f))
                lineTo(sx(0.78f), sy(0.86f))
                lineTo(sx(0.74f), sy(0.76f))
                lineTo(sx(0.66f), sy(0.72f))
                lineTo(sx(0.64f), sy(0.37f))
                lineTo(sx(0.72f), sy(0.31f))
                lineTo(sx(0.72f), sy(0.17f))
                lineTo(sx(0.61f), sy(0.17f))
                lineTo(sx(0.61f), sy(0.24f))
                lineTo(sx(0.55f), sy(0.24f))
                lineTo(sx(0.55f), sy(0.17f))
                lineTo(sx(0.45f), sy(0.17f))
                lineTo(sx(0.45f), sy(0.24f))
                lineTo(sx(0.39f), sy(0.24f))
                lineTo(sx(0.39f), sy(0.17f))
                lineTo(sx(0.28f), sy(0.17f))
                lineTo(sx(0.28f), sy(0.31f))
                lineTo(sx(0.36f), sy(0.37f))
                lineTo(sx(0.34f), sy(0.72f))
                lineTo(sx(0.26f), sy(0.76f))
                close()
            }
            drawPath(path, brush = fillBrush)
            drawPath(path, color = strokeColor, style = Stroke(strokeWidth, join = StrokeJoin.Round))
            drawLine(accentColor, Offset(sx(0.36f), sy(0.37f)), Offset(sx(0.64f), sy(0.37f)), strokeWidth)
            drawLine(accentColor, Offset(sx(0.32f), sy(0.73f)), Offset(sx(0.68f), sy(0.73f)), strokeWidth)
        }

        PieceType.KNIGHT -> {
            val body = Path().apply {
                moveTo(sx(0.23f), sy(0.86f))
                lineTo(sx(0.77f), sy(0.86f))
                lineTo(sx(0.74f), sy(0.75f))
                cubicTo(sx(0.74f), sy(0.46f), sx(0.68f), sy(0.24f), sx(0.49f), sy(0.16f))
                lineTo(sx(0.46f), sy(0.11f))
                lineTo(sx(0.39f), sy(0.18f))
                lineTo(sx(0.34f), sy(0.14f))
                lineTo(sx(0.32f), sy(0.23f))
                cubicTo(sx(0.24f), sy(0.30f), sx(0.19f), sy(0.39f), sx(0.21f), sy(0.47f))
                cubicTo(sx(0.24f), sy(0.52f), sx(0.34f), sy(0.50f), sx(0.42f), sy(0.44f))
                cubicTo(sx(0.44f), sy(0.54f), sx(0.33f), sy(0.64f), sx(0.26f), sy(0.75f))
                close()
            }
            drawPath(body, brush = fillBrush)
            drawPath(body, color = strokeColor, style = Stroke(strokeWidth, join = StrokeJoin.Round))
            // Eye & mane accent
            drawCircle(
                color = accentColor,
                radius = squareSize * 0.025f,
                center = Offset(sx(0.36f), sy(0.30f))
            )
            val mane = Path().apply {
                moveTo(sx(0.55f), sy(0.24f))
                cubicTo(sx(0.64f), sy(0.36f), sx(0.66f), sy(0.52f), sx(0.63f), sy(0.69f))
            }
            drawPath(mane, color = accentColor, style = Stroke(strokeWidth * 0.9f, cap = StrokeCap.Round))
        }

        PieceType.BISHOP -> {
            val body = Path().apply {
                moveTo(sx(0.23f), sy(0.86f))
                lineTo(sx(0.77f), sy(0.86f))
                lineTo(sx(0.72f), sy(0.76f))
                lineTo(sx(0.60f), sy(0.70f))
                cubicTo(sx(0.66f), sy(0.53f), sx(0.66f), sy(0.34f), sx(0.50f), sy(0.21f))
                cubicTo(sx(0.34f), sy(0.34f), sx(0.34f), sy(0.53f), sx(0.40f), sy(0.70f))
                lineTo(sx(0.28f), sy(0.76f))
                close()
            }
            drawPath(body, brush = fillBrush)
            drawPath(body, color = strokeColor, style = Stroke(strokeWidth, join = StrokeJoin.Round))

            // Mitre finial
            val finialCenter = Offset(sx(0.50f), sy(0.155f))
            drawCircle(brush = fillBrush, radius = squareSize * 0.055f, center = finialCenter)
            drawCircle(color = strokeColor, radius = squareSize * 0.055f, center = finialCenter, style = Stroke(strokeWidth))

            // Mitre slit & cross
            drawLine(accentColor, Offset(sx(0.44f), sy(0.42f)), Offset(sx(0.56f), sy(0.33f)), strokeWidth, StrokeCap.Round)
            drawLine(accentColor, Offset(sx(0.50f), sy(0.45f)), Offset(sx(0.50f), sy(0.56f)), strokeWidth, StrokeCap.Round)
            drawLine(accentColor, Offset(sx(0.445f), sy(0.505f)), Offset(sx(0.555f), sy(0.505f)), strokeWidth, StrokeCap.Round)
        }

        PieceType.QUEEN -> {
            val crown = Path().apply {
                moveTo(sx(0.22f), sy(0.86f))
                lineTo(sx(0.78f), sy(0.86f))
                lineTo(sx(0.73f), sy(0.76f))
                lineTo(sx(0.66f), sy(0.70f))
                lineTo(sx(0.80f), sy(0.31f))
                lineTo(sx(0.63f), sy(0.47f))
                lineTo(sx(0.63f), sy(0.22f))
                lineTo(sx(0.50f), sy(0.44f))
                lineTo(sx(0.37f), sy(0.22f))
                lineTo(sx(0.37f), sy(0.47f))
                lineTo(sx(0.20f), sy(0.31f))
                lineTo(sx(0.34f), sy(0.70f))
                lineTo(sx(0.27f), sy(0.76f))
                close()
            }
            drawPath(crown, brush = fillBrush)
            drawPath(crown, color = strokeColor, style = Stroke(strokeWidth, join = StrokeJoin.Round))

            // Crown pearls
            val pearls = listOf(
                Offset(sx(0.19f), sy(0.28f)),
                Offset(sx(0.365f), sy(0.19f)),
                Offset(sx(0.50f), sy(0.15f)),
                Offset(sx(0.635f), sy(0.19f)),
                Offset(sx(0.81f), sy(0.28f))
            )
            pearls.forEach { center ->
                drawCircle(brush = fillBrush, radius = squareSize * 0.042f, center = center)
                drawCircle(color = strokeColor, radius = squareSize * 0.042f, center = center, style = Stroke(strokeWidth * 0.9f))
            }
            drawLine(accentColor, Offset(sx(0.34f), sy(0.70f)), Offset(sx(0.66f), sy(0.70f)), strokeWidth)
        }

        PieceType.KING -> {
            // Cross on top
            drawLine(strokeColor, Offset(sx(0.50f), sy(0.10f)), Offset(sx(0.50f), sy(0.26f)), strokeWidth * 1.4f, StrokeCap.Round)
            drawLine(strokeColor, Offset(sx(0.43f), sy(0.16f)), Offset(sx(0.57f), sy(0.16f)), strokeWidth * 1.4f, StrokeCap.Round)
            drawLine(accentColor, Offset(sx(0.50f), sy(0.11f)), Offset(sx(0.50f), sy(0.24f)), strokeWidth * 0.8f, StrokeCap.Round)
            drawLine(accentColor, Offset(sx(0.44f), sy(0.16f)), Offset(sx(0.56f), sy(0.16f)), strokeWidth * 0.8f, StrokeCap.Round)

            val body = Path().apply {
                moveTo(sx(0.22f), sy(0.86f))
                lineTo(sx(0.78f), sy(0.86f))
                lineTo(sx(0.73f), sy(0.76f))
                lineTo(sx(0.65f), sy(0.70f))
                cubicTo(sx(0.80f), sy(0.48f), sx(0.76f), sy(0.29f), sx(0.61f), sy(0.29f))
                cubicTo(sx(0.55f), sy(0.29f), sx(0.52f), sy(0.33f), sx(0.50f), sy(0.38f))
                cubicTo(sx(0.48f), sy(0.33f), sx(0.45f), sy(0.29f), sx(0.39f), sy(0.29f))
                cubicTo(sx(0.24f), sy(0.29f), sx(0.20f), sy(0.48f), sx(0.35f), sy(0.70f))
                lineTo(sx(0.27f), sy(0.76f))
                close()
            }
            drawPath(body, brush = fillBrush)
            drawPath(body, color = strokeColor, style = Stroke(strokeWidth, join = StrokeJoin.Round))
            drawLine(accentColor, Offset(sx(0.50f), sy(0.38f)), Offset(sx(0.50f), sy(0.68f)), strokeWidth)
            drawLine(accentColor, Offset(sx(0.35f), sy(0.70f)), Offset(sx(0.65f), sy(0.70f)), strokeWidth)
        }
    }
}
