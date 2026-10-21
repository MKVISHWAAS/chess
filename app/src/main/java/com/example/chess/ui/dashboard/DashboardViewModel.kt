package com.example.chess.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.ai.GeminiCoach
import com.example.chess.data.repo.ChessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ChessRepository,
    private val coach: GeminiCoach,
) : ViewModel() {
    val snapshot = repository.dashboard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.chess.domain.model.DashboardSnapshot())
    val profile = repository.signedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshCoaching() {
        viewModelScope.launch {
            val openings = repository.openings
            val snap = snapshot.value
            val text = coach.recommendations(
                blunderThemes = listOf("${snap.dueMistakes} due mistakes"),
                openings = snap.recentGames.map { it.opening }.distinct(),
            )
            repository.setCoachingSummary(text)
        }
    }
}
