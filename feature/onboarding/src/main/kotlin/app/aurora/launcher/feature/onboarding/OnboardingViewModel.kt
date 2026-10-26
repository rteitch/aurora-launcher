package app.aurora.launcher.feature.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.model.GridPreset
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.ThemeMode
import app.aurora.launcher.core.platform.RoleGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val isDefaultHome: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: LauncherSettingsRepository,
    private val roleGateway: RoleGateway,
) : ViewModel() {

    private val isDefaultFlow = MutableStateFlow(roleGateway.isDefaultHomeLauncher())

    val uiState: StateFlow<OnboardingUiState> = combine(
        settingsRepository.settings,
        isDefaultFlow,
    ) { settings, isDefault ->
        OnboardingUiState(
            settings = settings,
            isDefaultHome = isDefault,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState(isDefaultHome = roleGateway.isDefaultHomeLauncher()),
    )

    fun refreshDefaultRoleState() {
        isDefaultFlow.value = roleGateway.isDefaultHomeLauncher()
    }

    fun buildRequestRoleIntent(): Intent? = roleGateway.createRequestDefaultHomeIntent()

    fun selectTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun selectGridPreset(preset: GridPreset) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(gridPreset = preset) }
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            refreshDefaultRoleState()
            settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
            onDone()
        }
    }
}
