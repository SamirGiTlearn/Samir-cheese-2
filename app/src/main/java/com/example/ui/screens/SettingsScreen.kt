package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPreferences
import com.example.ui.theme.BoardTheme
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    currentUser: FirebaseUser?,
    onToggleSound: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSelectBoardTheme: (BoardTheme) -> Unit,
    onSelectDefaultTimer: (Int) -> Unit,
    onSavePlayerNames: (String, String) -> Unit,
    onToggleShowLegalMoves: (Boolean) -> Unit,
    onToggleAutoFlipOffline: (Boolean) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onBack: () -> Unit
) {
    var p1 by remember(preferences.player1Name) { mutableStateOf(preferences.player1Name) }
    var p2 by remember(preferences.player2Name) { mutableStateOf(preferences.player2Name) }
    val timerOptions = listOf(1, 3, 5, 10, 15)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
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
                // Appearance & Audio
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("APPEARANCE & AUDIO", style = MaterialTheme.typography.labelMedium, color = Gold400)

                        SettingsToggleRow(
                            title = "Dark Theme",
                            subtitle = "Obsidian & Gold tournament look",
                            checked = preferences.darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            testTag = "settings_dark_theme_switch"
                        )
                        SettingsToggleRow(
                            title = "Sound Effects",
                            subtitle = "Wooden move, capture, castle & check audio",
                            checked = preferences.soundEnabled,
                            onCheckedChange = onToggleSound,
                            testTag = "settings_sound_switch"
                        )
                        SettingsToggleRow(
                            title = "Show Legal Move Hints",
                            subtitle = "Highlight valid destination squares",
                            checked = preferences.showLegalMoves,
                            onCheckedChange = onToggleShowLegalMoves,
                            testTag = "settings_legal_hints_switch"
                        )
                        SettingsToggleRow(
                            title = "Auto-Flip Offline Board",
                            subtitle = "Rotate board after each turn in pass & play",
                            checked = preferences.autoFlipOfflineBoard,
                            onCheckedChange = onToggleAutoFlipOffline,
                            testTag = "settings_auto_flip_switch"
                        )

                        Text("Chessboard Theme", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BoardTheme.entries.forEach { theme ->
                                val selected = preferences.boardTheme == theme
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) Gold500 else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectBoardTheme(theme) },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row {
                                            Box(Modifier.size(16.dp).background(theme.lightSquare))
                                            Box(Modifier.size(16.dp).background(theme.darkSquare))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = theme.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) Gold400 else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Default Timer & Player Names
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("GAME DEFAULTS", style = MaterialTheme.typography.labelMedium, color = Gold400)
                        Text("Default Match Timer", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            timerOptions.forEach { mins ->
                                FilterChip(
                                    selected = preferences.defaultTimerMinutes == mins,
                                    onClick = { onSelectDefaultTimer(mins) },
                                    label = { Text("${mins}m") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = p1,
                            onValueChange = { p1 = it },
                            label = { Text("Offline Player 1 (White)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = p2,
                            onValueChange = { p2 = it },
                            label = { Text("Offline Player 2 (Black)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onSavePlayerNames(p1, p2) },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
                        ) {
                            Text("Save Player Names", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Google Account
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ONLINE ACCOUNT", style = MaterialTheme.typography.labelMedium, color = Gold400)
                        if (currentUser != null) {
                            Text(
                                text = "Signed in as ${currentUser.displayName ?: "Player"}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = currentUser.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onSignOutClick,
                                modifier = Modifier.testTag("settings_sign_out_btn")
                            ) {
                                Text("Sign Out", color = CrimsonCheck)
                            }
                        } else {
                            Text(
                                text = "Not signed in. Sign in with Google to play online multiplayer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onSignInClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color(0xFF14130F))
                            ) {
                                Text("Sign in with Google", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text(
                    text = "Chess Master – Made by Samir • v1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
