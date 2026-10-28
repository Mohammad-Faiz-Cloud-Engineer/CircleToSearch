package com.gimirick.circletosearch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gimirick.circletosearch.data.SettingsRepository
import com.gimirick.circletosearch.data.SettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val settings: StateFlow<SettingsState> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState()
    )

    fun setBallEnabled(enabled: Boolean) = viewModelScope.launch { repository.setBallEnabled(enabled) }
    fun setBallSize(sizeDp: Int) = viewModelScope.launch { repository.setBallSize(sizeDp) }
}
