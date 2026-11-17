package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.data.remote.OnlineRoom
import com.example.ui.components.MiniCapturedPieceIcon
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CrimsonCheck
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.OnlineViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLobbyScreen(
    onlineViewModel: OnlineViewModel,
    initialJoinCode: String? = null,
    focusJoinMode: Boolean = false,
    defaultTimerMinutes: Int = 10,
    onNavigateToRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    val myRoomsState by onlineViewModel.myRooms.collectAsStateWithLifecycle()
    val isBusy by onlineViewModel.lobbyBusy.collectAsStateWithLifecycle()
    val errorMsg by onlineViewModel.lobbyError.collectAsStateWithLifecycle()

    var selectedColor by remember { mutableStateOf(PieceColor.WHITE) }
    var selectedMinutes by remember { mutableIntStateOf(defaultTimerMinutes) }
    var joinCodeInput by remember { mutableStateOf(initialJoinCode.orEmpty()) }
    val timerOptions = listOf(1, 3, 5, 10, 15)

    LaunchedEffect(initialJoinCode) {
        if (!initialJoinCode.isNullOrBlank()) {
            joinCodeInput = initialJoinCode
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (focusJoinMode) "Join Private Room" else "Online Multiplayer",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("online_lobby_back_btn")) {
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
                if (errorMsg != null) {
                    Surface(
                        color = CrimsonCheck.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CrimsonCheck.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = CrimsonCheck,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Join Room Card (first when focusJoinMode == true, or after Create Room otherwise)
                val joinCard: @Composable () -> Unit = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldWin.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Key, contentDescription = null, tint = EmeraldWin)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Join Friend's Room",
                                    fontFamily = CinzelFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Enter your friend's Room ID or paste the private invitation link.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = joinCodeInput,
                                onValueChange = {
                                    onlineViewModel.clearLobbyError()
                                    joinCodeInput = it
                                },
                                label = { Text("Room ID or Invitation Link") },
                                placeholder = { Text("e.g. CM7K9P") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("join_room_code_input")
                            )
                            Button(
                                onClick = {
                                    onlineViewModel.joinRoomByCode(joinCodeInput, onNavigateToRoom)
                                },
                                enabled = !isBusy && joinCodeInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("join_room_submit_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldWin,
                                    contentColor = Color(0xFF0A140F)
                                )
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Join Private Room", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                val createCard: @Composable () -> Unit = {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = Gold400)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Create Private Room",
                                    fontFamily = CinzelFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Gold400
                                )
                            }
                            Text(
                                text = "Choose your piece color and clock time, then invite a friend anywhere in the world.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text("Play As", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ColorChoiceCard(
                                    label = "White",
                                    color = PieceColor.WHITE,
                                    selected = selectedColor == PieceColor.WHITE,
                                    onClick = { selectedColor = PieceColor.WHITE },
                                    modifier = Modifier.weight(1f).testTag("choose_color_white")
                                )
                                ColorChoiceCard(
                                    label = "Black",
                                    color = PieceColor.BLACK,
                                    selected = selectedColor == PieceColor.BLACK,
                                    onClick = { selectedColor = PieceColor.BLACK },
                                    modifier = Modifier.weight(1f).testTag("choose_color_black")
                                )
                            }

                            Text("Timer (Minutes per Player)", style = MaterialTheme.typography.labelLarge)
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

                            Button(
                                onClick = {
                                    onlineViewModel.createPrivateRoom(selectedColor, selectedMinutes, onNavigateToRoom)
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("create_room_submit_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold500,
                                    contentColor = Color(0xFF14130F)
                                )
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Create Room & Get Invite Link", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (focusJoinMode) {
                    joinCard()
                    createCard()
                } else {
                    createCard()
                    joinCard()
                }

                // Active / Waiting / Recent Rooms for Reconnection
                when (val rooms = myRoomsState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gold400)
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = rooms.message,
                            color = CrimsonCheck,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is UiState.Success -> {
                        val openOrRecent = rooms.data.filter { it.status in listOf("ACTIVE", "WAITING", "FINISHED") }.take(10)
                        if (openOrRecent.isNotEmpty()) {
                            Text(
                                text = "YOUR ROOMS & RECONNECT",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold400
                            )
                            openOrRecent.forEach { room ->
                                RoomReconnectRow(
                                    room = room,
                                    onOpen = { onNavigateToRoom(room.roomCode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceCard(
    label: String,
    color: PieceColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Gold500 else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Gold500.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MiniCapturedPieceIcon(type = PieceType.KING, color = color, size = 26.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Gold400 else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RoomReconnectRow(
    room: OnlineRoom,
    onOpen: () -> Unit
) {
    val isLive = room.status == "ACTIVE" || room.status == "WAITING"
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isLive) EmeraldWin.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = if (isLive) EmeraldWin else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Room ${room.roomCode} • ${room.status}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${room.whiteName.ifBlank { "Open White" }} vs ${room.blackName.ifBlank { "Open Black" }} • ${room.timerMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Open room",
                tint = Gold400
            )
        }
    }
}
