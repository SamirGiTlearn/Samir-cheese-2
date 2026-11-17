package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ChessSoundManager
import com.example.chess.engine.ChessEngine
import com.example.chess.engine.PieceColor
import com.example.chess.engine.Square
import com.example.data.local.ChessDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.ui.components.ChessBoard
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.BoardTheme
import com.example.ui.theme.ChessMasterTheme
import com.example.ui.viewmodel.OfflineGameViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChessMasterRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: ChessDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var soundManager: ChessSoundManager
    private val activeViewModels = mutableListOf<OfflineGameViewModel>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChessDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        soundManager = ChessSoundManager().apply { isSoundEnabled = false }
    }

    @After
    fun teardown() {
        activeViewModels.forEach { it.stopClock() }
        activeViewModels.clear()
        db.close()
    }

    private fun idleUntil(timeoutMs: Long = 5000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition not met within ${timeoutMs}ms", predicate())
    }

    @Test
    fun `home screen displays branding and all primary menu entries without login`() {
        composeRule.setContent {
            ChessMasterTheme(darkTheme = true) {
                HomeScreen(
                    currentUser = null,
                    soundEnabled = true,
                    savedOfflineGame = null,
                    activeOnlineRooms = emptyList(),
                    onToggleSound = {},
                    onPlayOffline = {},
                    onPlayOnline = {},
                    onPlayWithFriend = {},
                    onJoinRoom = {},
                    onResumeOnlineRoom = {},
                    onOpenHistory = {},
                    onOpenStats = {},
                    onOpenHowToPlay = {},
                    onOpenSettings = {},
                    onOpenAuth = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chess Master – Made by Samir").assertIsDisplayed()
        composeRule.onNodeWithTag("home_play_offline_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_play_online_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_play_with_friend_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_join_room_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_history_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_stats_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_how_to_play_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `chessboard permanently displays Made by Samir watermark`() {
        composeRule.setContent {
            ChessMasterTheme(darkTheme = true) {
                ChessBoard(
                    position = ChessEngine.initialPosition(),
                    boardTheme = BoardTheme.ROYAL_WALNUT,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    lastMoveFrom = null,
                    lastMoveTo = null,
                    onSquareTapped = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chessboard_watermark_samir").assertIsDisplayed()
        composeRule.onNodeWithText("Made by Samir").assertIsDisplayed()
    }

    @Test
    fun `offline viewmodel plays moves, undoes, restarts, and persists across restart without internet`() {
        val vm1 = OfflineGameViewModel(db.chessDao(), prefsRepo, soundManager).also { activeViewModels.add(it) }
        idleUntil { vm1.uiState.value.isInitialized }
        assertEquals(PieceColor.WHITE, vm1.uiState.value.position.turn)

        // Play 1. e4 e5
        vm1.onSquareTapped(Square.fromAlgebraic("e2")!!)
        vm1.onSquareTapped(Square.fromAlgebraic("e4")!!)
        idleUntil { vm1.uiState.value.sanHistory == listOf("e4") }
        assertEquals(PieceColor.BLACK, vm1.uiState.value.position.turn)

        vm1.onSquareTapped(Square.fromAlgebraic("e7")!!)
        vm1.onSquareTapped(Square.fromAlgebraic("e5")!!)
        idleUntil { vm1.uiState.value.sanHistory == listOf("e4", "e5") }

        // Ensure Room write completed before simulating restart
        idleUntil {
            val saved = kotlinx.coroutines.runBlocking { db.chessDao().getOfflineGameOnce() }
            saved != null && saved.sanHistoryJson.contains("e5")
        }
        vm1.stopClock()

        // Simulate app restart by creating a new ViewModel backed by the same Room DB
        val vmAfterRestart = OfflineGameViewModel(db.chessDao(), prefsRepo, soundManager).also { activeViewModels.add(it) }
        idleUntil { vmAfterRestart.uiState.value.isInitialized }
        assertEquals(listOf("e4", "e5"), vmAfterRestart.uiState.value.sanHistory)
        assertEquals(PieceColor.WHITE, vmAfterRestart.uiState.value.position.turn)

        // Undo move
        vmAfterRestart.undoMove()
        idleUntil { vmAfterRestart.uiState.value.sanHistory == listOf("e4") }
        assertEquals(PieceColor.BLACK, vmAfterRestart.uiState.value.position.turn)

        // Restart game
        vmAfterRestart.restartCurrentGame()
        idleUntil { vmAfterRestart.uiState.value.sanHistory.isEmpty() }
        assertEquals(ChessEngine.INITIAL_FEN, ChessEngine.toFen(vmAfterRestart.uiState.value.position))
    }

    @Test
    fun `countdown timer decrements active player, supports pause, and triggers game over on timeout`() {
        var fakeNowMs = 1_000_000L
        val vm = OfflineGameViewModel(
            chessDao = db.chessDao(),
            prefsRepo = prefsRepo,
            soundManager = soundManager,
            timeProviderMs = { fakeNowMs }
        ).also { activeViewModels.add(it) }
        idleUntil { vm.uiState.value.isInitialized }

        vm.startNewGame(timerMinutes = 1, whiteName = "Samir", blackName = "Opponent")
        idleUntil { vm.uiState.value.whiteTimeMs == 60_000L && vm.uiState.value.blackTimeMs == 60_000L }

        // White's turn: 5 seconds elapse -> White has 55s, Black still has 60s
        kotlinx.coroutines.runBlocking { vm.applyClockElapsed(5_000L) }
        assertEquals(55_000L, vm.uiState.value.whiteTimeMs)
        assertEquals(60_000L, vm.uiState.value.blackTimeMs)

        // Pause clock: elapsed time must not deduct from either player
        vm.togglePause()
        assertTrue(vm.uiState.value.isPaused)
        kotlinx.coroutines.runBlocking { vm.applyClockElapsed(10_000L) }
        assertEquals(55_000L, vm.uiState.value.whiteTimeMs)
        assertEquals(60_000L, vm.uiState.value.blackTimeMs)

        // White plays e2-e4 -> automatically unpauses and switches turn to Black
        vm.onSquareTapped(Square.fromAlgebraic("e2")!!)
        vm.onSquareTapped(Square.fromAlgebraic("e4")!!)
        idleUntil { vm.uiState.value.position.turn == PieceColor.BLACK && !vm.uiState.value.isPaused }

        // Black's turn: 60 seconds elapse -> Black hits 0ms, White wins by TIMEOUT
        kotlinx.coroutines.runBlocking { vm.applyClockElapsed(60_000L) }
        val state = vm.uiState.value
        assertEquals(0L, state.blackTimeMs)
        assertEquals(55_000L, state.whiteTimeMs)
        assertTrue(state.showGameOverDialog)
        val finished = state.status as com.example.chess.engine.GameStatus.Finished
        assertEquals(PieceColor.WHITE, finished.winner)
        assertEquals(com.example.chess.engine.GameTermination.TIMEOUT, finished.termination)

        // Verify persisted finished state in Room
        val saved = kotlinx.coroutines.runBlocking { db.chessDao().getOfflineGameOnce() }
        assertEquals("FINISHED", saved?.status)
        assertEquals("WHITE", saved?.winner)
        assertEquals("TIMEOUT", saved?.termination)
    }

    @Test
    fun `offline screen displays both clocks and game over dialog when time reaches zero`() {
        var fakeNowMs = 2_000_000L
        val vm = OfflineGameViewModel(
            chessDao = db.chessDao(),
            prefsRepo = prefsRepo,
            soundManager = soundManager,
            timeProviderMs = { fakeNowMs }
        ).also { activeViewModels.add(it) }
        idleUntil { vm.uiState.value.isInitialized }
        vm.startNewGame(timerMinutes = 1, whiteName = "Samir", blackName = "Alex")
        idleUntil { vm.uiState.value.whiteTimeMs == 60_000L }

        composeRule.setContent {
            ChessMasterTheme(darkTheme = true) {
                com.example.ui.screens.OfflineGameScreen(
                    viewModel = vm,
                    preferences = com.example.data.local.UserPreferences(),
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_clock_black").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("player_clock_white").performScrollTo().assertIsDisplayed()

        // Expire White's clock -> Black wins on time and Game Over modal appears
        kotlinx.coroutines.runBlocking { vm.applyClockElapsed(60_000L) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_over_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Alex Wins!").assertIsDisplayed()
        composeRule.onNodeWithText("Samir ran out of time after 0 moves.").assertIsDisplayed()
    }
}
