package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.ui.components.MiniCapturedPieceIcon
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "How to Play Chess",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("howto_back_btn")) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PIECE MOVEMENTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold400
                )

                PieceGuideCard(PieceType.KING, "King", "Moves 1 square in any direction. Can also Castle once per game if neither King nor Rook has moved, squares between them are clear, and the King does not move through or into check.")
                PieceGuideCard(PieceType.QUEEN, "Queen", "Moves any number of squares horizontally, vertically, or diagonally.")
                PieceGuideCard(PieceType.ROOK, "Rook", "Moves any number of squares horizontally or vertically. Participates in castling.")
                PieceGuideCard(PieceType.BISHOP, "Bishop", "Moves any number of squares diagonally along its square color.")
                PieceGuideCard(PieceType.KNIGHT, "Knight", "Moves in an L-shape (2 squares in one direction, then 1 perpendicular). Can jump over other pieces.")
                PieceGuideCard(PieceType.PAWN, "Pawn", "Moves forward 1 square (or 2 from its starting rank) and captures diagonally 1 square forward. Supports En Passant and Pawn Promotion upon reaching the 8th rank.")

                Text(
                    text = "SPECIAL RULES & ENDINGS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold400
                )

                RuleInfoCard(
                    title = "Check & Checkmate",
                    body = "When a King is under attack, it is in Check and must escape immediately. If no legal move removes the threat, it is Checkmate and the game ends."
                )
                RuleInfoCard(
                    title = "En Passant & Pawn Promotion",
                    body = "If a Pawn advances two squares past an enemy Pawn on an adjacent file, it may be captured En Passant on the very next turn. Any Pawn reaching the far rank promotes to a Queen, Rook, Bishop, or Knight."
                )
                RuleInfoCard(
                    title = "Draw Conditions",
                    body = "A match ends in a Draw on Stalemate (no legal moves while not in check), Threefold Repetition, the Fifty-Move Rule (50 consecutive moves without pawn moves or captures), Insufficient Material, or Mutual Draw Agreement."
                )
                RuleInfoCard(
                    title = "Online Multiplayer with Friends",
                    body = "Tap Play With Friend to generate a private Room Code and shareable invitation link. Both devices synchronize moves in real time, validate every turn on the server, and automatically reconnect if your network drops."
                )
            }
        }
    }
}

@Composable
private fun PieceGuideCard(type: PieceType, name: String, description: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniCapturedPieceIcon(type = type, color = PieceColor.WHITE, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Gold400)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RuleInfoCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Gold500.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Gold400)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
