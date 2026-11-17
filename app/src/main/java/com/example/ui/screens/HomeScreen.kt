package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.OfflineGameEntity
import com.example.data.remote.OnlineRoom
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.google.firebase.auth.FirebaseUser

@Composable
fun HomeScreen(
    currentUser: FirebaseUser?,
    soundEnabled: Boolean,
    savedOfflineGame: OfflineGameEntity?,
    activeOnlineRooms: List<OnlineRoom>,
    onToggleSound: () -> Unit,
    onPlayOffline: () -> Unit,
    onPlayOnline: () -> Unit,
    onPlayWithFriend: () -> Unit,
    onJoinRoom: () -> Unit,
    onResumeOnlineRoom: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenHowToPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val scrollState = rememberScrollState()
    val reconnectRoom = activeOnlineRooms.firstOrNull { it.status == "ACTIVE" || it.status == "WAITING" }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Brand Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_chess_king_fg_1794940278307),
                            contentDescription = "Chess Master Logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.dp, Gold500.copy(alpha = 0.7f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CHESS MASTER",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Gold400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Made by Samir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleSound,
                            modifier = Modifier.testTag("home_sound_toggle")
                        ) {
                            Icon(
                                imageVector = if (soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = if (soundEnabled) "Mute sounds" else "Enable sounds",
                                tint = Gold400
                            )
                        }
                        Surface(
                            onClick = onOpenAuth,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .border(1.dp, Gold500.copy(alpha = 0.45f), CircleShape)
                                .testTag("home_profile_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentUser?.photoUrl != null) {
                                    AsyncImage(
                                        model = currentUser.photoUrl,
                                        contentDescription = "Account avatar",
                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = "Account",
                                        tint = Gold400,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentUser?.displayName?.substringBefore(" ") ?: "Sign In",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Hero Showcase Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Gold500.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_chess_hero_1794940279582),
                            contentDescription = "Luxury Wooden Chessboard",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x330C0B09),
                                            Color(0xCC0C0B09),
                                            Color(0xF50C0B09)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Chess Master – Made by Samir",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFFF6F1E7)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Official FIDE rules • Offline Pass & Play • Real-Time Online Rooms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Gold400
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onPlayOffline,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_play_offline_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold500,
                                        contentColor = Color(0xFF14130F)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play Offline", fontWeight = FontWeight.Bold, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = onPlayOnline,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_play_online_btn"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold400),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play Online", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Active Online Match Reconnect Banner
                if (reconnectRoom != null) {
                    Surface(
                        onClick = { onResumeOnlineRoom(reconnectRoom.roomCode) },
                        shape = RoundedCornerShape(16.dp),
                        color = EmeraldWin.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldWin.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .testTag("home_reconnect_online_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = EmeraldWin)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (reconnectRoom.status == "WAITING") {
                                            "Waiting Room Ready (${reconnectRoom.roomCode})"
                                        } else {
                                            "Reconnect to Online Match (${reconnectRoom.roomCode})"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldWin
                                    )
                                    Text(
                                        text = "${reconnectRoom.whiteName.ifBlank { "White" }} vs ${reconnectRoom.blackName.ifBlank { "Waiting..." }} • Tap to resume",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Offline Saved Match Quick Status
                if (savedOfflineGame != null && savedOfflineGame.status == "ONGOING") {
                    Surface(
                        onClick = onPlayOffline,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Gold500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .testTag("home_resume_offline_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Saved Offline Match Ready",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Gold400
                                )
                                Text(
                                    text = "${savedOfflineGame.whiteName} vs ${savedOfflineGame.blackName} • Tap to continue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "RESUME",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Multiplayer Quick Actions
                Text(
                    text = "PLAY & COMPETE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold400,
                    letterSpacing = 1.4.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeMenuTile(
                        title = "Play With Friend",
                        subtitle = "Create private room & invite link",
                        icon = Icons.Rounded.GroupAdd,
                        accent = Gold400,
                        onClick = onPlayWithFriend,
                        modifier = Modifier.weight(1f).testTag("home_play_with_friend_btn")
                    )
                    HomeMenuTile(
                        title = "Join Room",
                        subtitle = "Enter Room ID or invite link",
                        icon = Icons.Rounded.Key,
                        accent = EmeraldWin,
                        onClick = onJoinRoom,
                        modifier = Modifier.weight(1f).testTag("home_join_room_btn")
                    )
                }

                // Library & Tools
                Text(
                    text = "CAREER & SETTINGS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold400,
                    letterSpacing = 1.4.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeMenuTile(
                        title = "Game History",
                        subtitle = "PGN archive & replay",
                        icon = Icons.Rounded.History,
                        accent = Gold400,
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f).testTag("home_history_btn")
                    )
                    HomeMenuTile(
                        title = "Player Statistics",
                        subtitle = "Wins, losses & draws",
                        icon = Icons.Rounded.BarChart,
                        accent = Gold400,
                        onClick = onOpenStats,
                        modifier = Modifier.weight(1f).testTag("home_stats_btn")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeMenuTile(
                        title = "How to Play",
                        subtitle = "Chess rules & tactics",
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        accent = Gold400,
                        onClick = onOpenHowToPlay,
                        modifier = Modifier.weight(1f).testTag("home_how_to_play_btn")
                    )
                    HomeMenuTile(
                        title = "Settings",
                        subtitle = "Themes, sound & names",
                        icon = Icons.Rounded.Settings,
                        accent = Gold400,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f).testTag("home_settings_btn")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun HomeMenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
