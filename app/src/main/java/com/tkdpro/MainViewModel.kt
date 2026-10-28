package com.tkdpro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkdpro.core.datastore.AppSettings
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootState(val settings: AppSettings, val locked: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private val unlocked = MutableStateFlow(savedState.get<Boolean>(KEY_UNLOCKED) ?: false)

    val state: StateFlow<RootState?> = combine(settings.settings, unlocked) { s, u ->
        RootState(s, locked = s.appLockEnabled && !u)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun unlock() {
        unlocked.value = true
        savedState[KEY_UNLOCKED] = true
    }

    fun lock() {
        unlocked.value = false
        savedState[KEY_UNLOCKED] = false
    }

    fun syncOnForeground() {
        viewModelScope.launch { if (settings.current().syncEnabled) syncScheduler.schedule(delaySeconds = 0) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingCompleted() }
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
    }
}
