package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.GameHistoryEntity
import com.example.data.remote.UserProfile
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    historyList: List<GameHistoryEntity>,
    currentUser: FirebaseUser?,
    cloudProfile: UserProfile?,
    onSignInForCloudStats: () -> Unit,
    onBack: () -> Unit
) {
    val offlineGames = historyList.filter { it.mode == "OFFLINE" }
    val offlineWhiteWins = offlineGames.count { it.winner == "WHITE" }
    val offlineBlackWins = offlineGames.count { it.winner == "BLACK" }
    val offlineDraws = offlineGames.count { it.winner == "DRAW" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Player Statistics",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("stats_back_btn")) {
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
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Online Career Stats
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Gold500.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Online Multiplayer Career",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Gold400
                        )

                        if (currentUser == null) {
                            Text(
                                text = "Sign in with Google to track your worldwide wins, losses, and draws across devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onSignInForCloudStats,
                                colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
                            ) {
                                Text("Sign in with Google", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val wins = cloudProfile?.wins?.toInt() ?: 0
                            val losses = cloudProfile?.losses?.toInt() ?: 0
                            val draws = cloudProfile?.draws?.toInt() ?: 0
                            val total = (cloudProfile?.gamesPlayed?.toInt() ?: (wins + losses + draws))
                            val winRate = if (total > 0) (wins.toFloat() / total.toFloat()) else 0f

                            Text(
                                text = "${cloudProfile?.displayName ?: currentUser.displayName ?: "Player"} • $total Online Games",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatPill("Wins", wins.toString(), EmeraldWin, Modifier.weight(1f))
                                StatPill("Draws", draws.toString(), Gold400, Modifier.weight(1f))
                                StatPill("Losses", losses.toString(), CrimsonCheck, Modifier.weight(1f))
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Win Rate", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${(winRate * 100).toInt()}%",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Gold400
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { winRate },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = EmeraldWin,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }

                // Offline Pass & Play Stats
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Offline Two-Player Summary",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${offlineGames.size} Completed Local Matches",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatPill("White Wins", offlineWhiteWins.toString(), Gold400, Modifier.weight(1f))
                            StatPill("Draws", offlineDraws.toString(), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                            StatPill("Black Wins", offlineBlackWins.toString(), EmeraldWin, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
