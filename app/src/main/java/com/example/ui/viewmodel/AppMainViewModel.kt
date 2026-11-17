package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChessSoundManager
import com.example.data.local.ChessDao
import com.example.data.local.GameHistoryEntity
import com.example.data.local.OfflineGameEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.ui.theme.BoardTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppMainViewModel(
    private val chessDao: ChessDao,
    private val prefsRepo: UserPreferencesRepository,
    val soundManager: ChessSoundManager
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences
        .onEach { soundManager.isSoundEnabled = it.soundEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    val savedOfflineGame: StateFlow<OfflineGameEntity?> = chessDao.observeOfflineGame()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val gameHistory: StateFlow<List<GameHistoryEntity>> = chessDao.observeGameHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun setSoundEnabled(enabled: Boolean) {
        soundManager.isSoundEnabled = enabled
        viewModelScope.launch { prefsRepo.setSoundEnabled(enabled) }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkTheme(dark) }
    }

    fun setBoardTheme(theme: BoardTheme) {
        viewModelScope.launch { prefsRepo.setBoardTheme(theme) }
    }

    fun setDefaultTimerMinutes(minutes: Int) {
        viewModelScope.launch { prefsRepo.setDefaultTimerMinutes(minutes) }
    }

    fun setPlayerNames(p1: String, p2: String) {
        viewModelScope.launch { prefsRepo.setPlayerNames(p1, p2) }
    }

    fun setShowLegalMoves(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowLegalMoves(show) }
    }

    fun setAutoFlipOfflineBoard(autoFlip: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoFlipOfflineBoard(autoFlip) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { chessDao.deleteHistoryById(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { chessDao.clearAllHistory() }
    }
}
