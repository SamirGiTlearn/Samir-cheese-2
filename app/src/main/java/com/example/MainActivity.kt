package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.ChessSoundManager
import com.example.data.local.ChessDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.OnlineChessRepository
import com.example.ui.auth.OnlineAuthGateScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutGoogle
import com.example.ui.screens.GameHistoryScreen
import com.example.ui.screens.GameReplayScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HowToPlayScreen
import com.example.ui.screens.OfflineGameScreen
import com.example.ui.screens.OnlineLobbyScreen
import com.example.ui.screens.OnlineRoomScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.ChessMasterTheme
import com.example.ui.viewmodel.AppMainViewModel
import com.example.ui.viewmodel.OfflineGameViewModel
import com.example.ui.viewmodel.OnlineViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HOME = "home"
    const val OFFLINE_GAME = "offline_game"
    const val ONLINE_LOBBY = "online_lobby?joinCode={joinCode}&focusJoin={focusJoin}"
    const val ONLINE_ROOM = "online_room/{roomCode}"
    const val AUTH_GATE = "auth_gate"
    const val HISTORY = "history"
    const val REPLAY = "replay/{gameId}"
    const val STATS = "stats"
    const val HOW_TO_PLAY = "how_to_play"
    const val SETTINGS = "settings"

    fun onlineLobby(joinCode: String = "", focusJoin: Boolean = false): String =
        "online_lobby?joinCode=${Uri.encode(joinCode)}&focusJoin=$focusJoin"

    fun onlineRoom(roomCode: String): String = "online_room/${Uri.encode(roomCode)}"

    fun replay(gameId: Long): String = "replay/$gameId"
}

class MainActivity : ComponentActivity() {

    private var pendingDeepLinkRoomCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLinkRoomCode = extractRoomCodeFromIntent(intent)

        setContent {
            ChessMasterApp(
                pendingDeepLinkCode = pendingDeepLinkRoomCode,
                onDeepLinkConsumed = { pendingDeepLinkRoomCode = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractRoomCodeFromIntent(intent)
        if (!extracted.isNullOrBlank()) {
            pendingDeepLinkRoomCode = extracted
        }
    }

    private fun extractRoomCodeFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val queryCode = data.getQueryParameter("code")
        if (!queryCode.isNullOrBlank()) {
            return OnlineChessRepository.normalizeRoomCode(queryCode)
        }
        val pathSeg = data.lastPathSegment
        if (!pathSeg.isNullOrBlank() && pathSeg != "room" && pathSeg != "join") {
            return OnlineChessRepository.normalizeRoomCode(pathSeg)
        }
        return null
    }
}

@Composable
fun ChessMasterApp(
    pendingDeepLinkCode: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundManager = remember { ChessSoundManager() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val mainViewModel: AppMainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = ChessDatabase.getInstance(app)
                AppMainViewModel(
                    chessDao = db.chessDao(),
                    prefsRepo = UserPreferencesRepository(app),
                    soundManager = soundManager
                )
            }
        }
    )

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val savedOfflineGame by mainViewModel.savedOfflineGame.collectAsStateWithLifecycle()
    val gameHistory by mainViewModel.gameHistory.collectAsStateWithLifecycle()
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    // Attempt silent Google Auto-Sign-In on startup only if previously authorized
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope
        )
    }

    ChessMasterTheme(darkTheme = preferences.darkTheme) {
        val navController = rememberNavController()

        // Navigate directly into Join Room if opened via invitation deep link
        LaunchedEffect(pendingDeepLinkCode) {
            if (!pendingDeepLinkCode.isNullOrBlank()) {
                val code = pendingDeepLinkCode
                onDeepLinkConsumed()
                if (Firebase.auth.currentUser != null) {
                    navController.navigate(Routes.onlineLobby(joinCode = code, focusJoin = true))
                } else {
                    navController.navigate(Routes.AUTH_GATE)
                }
            }
        }

        val currentUid = currentUser?.uid

        // Instantiate OnlineViewModel ONLY when authenticated, keyed strictly by UID
        val onlineViewModel: OnlineViewModel? = if (currentUid != null) {
            key(currentUid) {
                viewModel(
                    key = currentUid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) as Application
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val firestore = FirebaseFirestore.getInstance(databaseId)
                            val roomDb = ChessDatabase.getInstance(app)
                            OnlineViewModel(
                                onlineRepo = OnlineChessRepository(firestore),
                                chessDao = roomDb.chessDao(),
                                soundManager = soundManager,
                                currentUserId = currentUid
                            )
                        }
                    }
                )
            }
        } else null

        val myRoomsState = if (onlineViewModel != null) {
            onlineViewModel.myRooms.collectAsStateWithLifecycle().value
        } else null

        val profileState = if (onlineViewModel != null) {
            onlineViewModel.userProfile.collectAsStateWithLifecycle().value
        } else null

        val activeRoomsList = (myRoomsState as? UiState.Success)?.data ?: emptyList()
        val cloudProfile = (profileState as? UiState.Success)?.data

        fun navigateToOnline(joinCode: String = "", focusJoin: Boolean = false) {
            if (Firebase.auth.currentUser != null) {
                navController.navigate(Routes.onlineLobby(joinCode, focusJoin))
            } else {
                navController.navigate(Routes.AUTH_GATE)
            }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    currentUser = currentUser,
                    soundEnabled = preferences.soundEnabled,
                    savedOfflineGame = savedOfflineGame,
                    activeOnlineRooms = activeRoomsList,
                    onToggleSound = { mainViewModel.setSoundEnabled(!preferences.soundEnabled) },
                    onPlayOffline = { navController.navigate(Routes.OFFLINE_GAME) },
                    onPlayOnline = { navigateToOnline() },
                    onPlayWithFriend = { navigateToOnline(focusJoin = false) },
                    onJoinRoom = { navigateToOnline(focusJoin = true) },
                    onResumeOnlineRoom = { code ->
                        if (Firebase.auth.currentUser != null) {
                            navController.navigate(Routes.onlineRoom(code))
                        } else {
                            navController.navigate(Routes.AUTH_GATE)
                        }
                    },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenHowToPlay = { navController.navigate(Routes.HOW_TO_PLAY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAuth = {
                        if (currentUser == null) navController.navigate(Routes.AUTH_GATE)
                        else navController.navigate(Routes.STATS)
                    }
                )
            }

            composable(Routes.OFFLINE_GAME) {
                val offlineVm: OfflineGameViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) as Application
                            val db = ChessDatabase.getInstance(app)
                            OfflineGameViewModel(
                                chessDao = db.chessDao(),
                                prefsRepo = UserPreferencesRepository(app),
                                soundManager = soundManager
                            )
                        }
                    }
                )
                OfflineGameScreen(
                    viewModel = offlineVm,
                    preferences = preferences,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AUTH_GATE) {
                OnlineAuthGateScreen(
                    onAuthSuccess = {
                        navController.navigate(Routes.onlineLobby()) {
                            popUpTo(Routes.AUTH_GATE) { inclusive = true }
                        }
                    },
                    onBackToHome = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ONLINE_LOBBY,
                arguments = listOf(
                    navArgument("joinCode") { type = NavType.StringType; defaultValue = "" },
                    navArgument("focusJoin") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                if (onlineViewModel == null) {
                    OnlineAuthGateScreen(
                        onAuthSuccess = {},
                        onBackToHome = { navController.popBackStack() }
                    )
                } else {
                    val joinCode = backStackEntry.arguments?.getString("joinCode").orEmpty()
                    val focusJoin = backStackEntry.arguments?.getBoolean("focusJoin") ?: false
                    OnlineLobbyScreen(
                        onlineViewModel = onlineViewModel,
                        initialJoinCode = joinCode,
                        focusJoinMode = focusJoin,
                        defaultTimerMinutes = preferences.defaultTimerMinutes,
                        onNavigateToRoom = { code ->
                            navController.navigate(Routes.onlineRoom(code))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Routes.ONLINE_ROOM,
                arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
            ) { backStackEntry ->
                if (onlineViewModel == null) {
                    OnlineAuthGateScreen(
                        onAuthSuccess = {},
                        onBackToHome = { navController.popBackStack() }
                    )
                } else {
                    val roomCode = backStackEntry.arguments?.getString("roomCode").orEmpty()
                    OnlineRoomScreen(
                        roomCode = roomCode,
                        onlineViewModel = onlineViewModel,
                        preferences = preferences,
                        onNavigateToRoom = { nextCode ->
                            navController.navigate(Routes.onlineRoom(nextCode)) {
                                popUpTo(Routes.ONLINE_ROOM) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.HISTORY) {
                GameHistoryScreen(
                    historyList = gameHistory,
                    onSelectGameForReplay = { gameId ->
                        navController.navigate(Routes.replay(gameId))
                    },
                    onDeleteGame = { id -> mainViewModel.deleteHistoryItem(id) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.REPLAY,
                arguments = listOf(navArgument("gameId") { type = NavType.LongType })
            ) { backStackEntry ->
                val gameId = backStackEntry.arguments?.getLong("gameId") ?: -1L
                val game = gameHistory.firstOrNull { it.id == gameId }
                GameReplayScreen(
                    game = game,
                    preferences = preferences,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STATS) {
                StatsScreen(
                    historyList = gameHistory,
                    currentUser = currentUser,
                    cloudProfile = cloudProfile,
                    onSignInForCloudStats = { navController.navigate(Routes.AUTH_GATE) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.HOW_TO_PLAY) {
                HowToPlayScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = preferences,
                    currentUser = currentUser,
                    onToggleSound = mainViewModel::setSoundEnabled,
                    onToggleDarkTheme = mainViewModel::setDarkTheme,
                    onSelectBoardTheme = mainViewModel::setBoardTheme,
                    onSelectDefaultTimer = mainViewModel::setDefaultTimerMinutes,
                    onSavePlayerNames = mainViewModel::setPlayerNames,
                    onToggleShowLegalMoves = mainViewModel::setShowLegalMoves,
                    onToggleAutoFlipOffline = mainViewModel::setAutoFlipOfflineBoard,
                    onSignInClick = { navController.navigate(Routes.AUTH_GATE) },
                    onSignOutClick = {
                        onlineViewModel?.stopObservingRoom()
                        signOutGoogle(credentialManager, scope)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
