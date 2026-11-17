package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.BoardTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chessDataStore by preferencesDataStore(name = "chess_master_prefs")

data class UserPreferences(
    val soundEnabled: Boolean = true,
    val darkTheme: Boolean = true,
    val boardTheme: BoardTheme = BoardTheme.ROYAL_WALNUT,
    val defaultTimerMinutes: Int = 10,
    val player1Name: String = "Player 1 (White)",
    val player2Name: String = "Player 2 (Black)",
    val showLegalMoves: Boolean = true,
    val autoFlipOfflineBoard: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val BOARD_THEME = stringPreferencesKey("board_theme")
        val DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")
        val PLAYER1_NAME = stringPreferencesKey("player1_name")
        val PLAYER2_NAME = stringPreferencesKey("player2_name")
        val SHOW_LEGAL_MOVES = booleanPreferencesKey("show_legal_moves")
        val AUTO_FLIP_OFFLINE = booleanPreferencesKey("auto_flip_offline")
    }

    val preferences: Flow<UserPreferences> = context.chessDataStore.data.map { prefs ->
        val boardThemeName = prefs[Keys.BOARD_THEME]
        val boardTheme = BoardTheme.entries.firstOrNull { it.name == boardThemeName } ?: BoardTheme.ROYAL_WALNUT
        UserPreferences(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            boardTheme = boardTheme,
            defaultTimerMinutes = prefs[Keys.DEFAULT_TIMER_MINUTES] ?: 10,
            player1Name = prefs[Keys.PLAYER1_NAME]?.ifBlank { null } ?: "Player 1 (White)",
            player2Name = prefs[Keys.PLAYER2_NAME]?.ifBlank { null } ?: "Player 2 (Black)",
            showLegalMoves = prefs[Keys.SHOW_LEGAL_MOVES] ?: true,
            autoFlipOfflineBoard = prefs[Keys.AUTO_FLIP_OFFLINE] ?: false
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.chessDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.chessDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setBoardTheme(theme: BoardTheme) {
        context.chessDataStore.edit { it[Keys.BOARD_THEME] = theme.name }
    }

    suspend fun setDefaultTimerMinutes(minutes: Int) {
        context.chessDataStore.edit { it[Keys.DEFAULT_TIMER_MINUTES] = minutes }
    }

    suspend fun setPlayerNames(player1: String, player2: String) {
        context.chessDataStore.edit {
            it[Keys.PLAYER1_NAME] = player1.trim().take(30).ifBlank { "Player 1 (White)" }
            it[Keys.PLAYER2_NAME] = player2.trim().take(30).ifBlank { "Player 2 (Black)" }
        }
    }

    suspend fun setShowLegalMoves(show: Boolean) {
        context.chessDataStore.edit { it[Keys.SHOW_LEGAL_MOVES] = show }
    }

    suspend fun setAutoFlipOfflineBoard(autoFlip: Boolean) {
        context.chessDataStore.edit { it[Keys.AUTO_FLIP_OFFLINE] = autoFlip }
    }
}
