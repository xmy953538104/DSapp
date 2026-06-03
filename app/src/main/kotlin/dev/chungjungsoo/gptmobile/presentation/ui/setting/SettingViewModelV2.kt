package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.util.TokenUsageStats
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState: StateFlow<List<PlatformV2>> = _platformState.asStateFlow()

    private val _autoContextCompression = MutableStateFlow(true)
    val autoContextCompression: StateFlow<Boolean> = _autoContextCompression.asStateFlow()

    private val _appTestMode = MutableStateFlow(true)
    val appTestMode: StateFlow<Boolean> = _appTestMode.asStateFlow()

    private val _tokenUsageStats = MutableStateFlow<TokenUsageStats?>(null)
    val tokenUsageStats: StateFlow<TokenUsageStats?> = _tokenUsageStats.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    init {
        fetchPlatforms()
        fetchAutoContextCompression()
        fetchAppTestMode()
    }

    fun fetchPlatforms() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatformV2s()
            _platformState.update { platforms }
        }
    }

    private fun fetchAutoContextCompression() {
        viewModelScope.launch {
            _autoContextCompression.update { settingRepository.getAutoContextCompression() }
        }
    }

    fun updateAutoContextCompression(enabled: Boolean) {
        _autoContextCompression.update { enabled }
        viewModelScope.launch {
            settingRepository.updateAutoContextCompression(enabled)
        }
    }

    private fun fetchAppTestMode() {
        viewModelScope.launch {
            _appTestMode.update { settingRepository.getAppTestMode() }
        }
    }

    fun updateAppTestMode(enabled: Boolean) {
        _appTestMode.update { enabled }
        viewModelScope.launch {
            settingRepository.updateAppTestMode(enabled)
        }
    }

    fun openTokenStatsDialog() {
        _dialogState.update { it.copy(isTokenStatsDialogOpen = true) }
        viewModelScope.launch {
            _tokenUsageStats.update { chatRepository.getTokenUsageStats() }
        }
    }

    fun closeTokenStatsDialog() = _dialogState.update { it.copy(isTokenStatsDialogOpen = false) }

    suspend fun createDiagnosticReport(appLog: String): String {
        val platformsResult = runCatching { settingRepository.fetchPlatformV2s() }
        val tokenStatsResult = runCatching { chatRepository.getTokenUsageStats() }
        return buildString {
            appendLine("GPT Mobile Diagnostic Report")
            appendLine("Generated at: ${System.currentTimeMillis() / 1000}")
            appendLine()
            appendLine("Platforms:")
            platformsResult
                .onSuccess { platforms ->
                    platforms.forEach { platform ->
                        appendLine("- ${platform.name} (${platform.compatibleType}) enabled=${platform.enabled} model=${platform.model} reasoning=${platform.reasoning} url=${platform.apiUrl}")
                    }
                }
                .onFailure { appendLine("- failed: ${it.message}") }
            appendLine()
            appendLine("Token usage estimate:")
            tokenStatsResult
                .onSuccess { tokenStats ->
                    appendLine("- chats=${tokenStats.totalChats}")
                    appendLine("- messages=${tokenStats.totalMessages}")
                    appendLine("- estimatedTokens=${tokenStats.totalEstimatedTokens}")
                    appendLine("- compactedTokensSaved=${tokenStats.totalCompactedTokensSaved}")
                }
                .onFailure { appendLine("- failed: ${it.message}") }
            appendLine()
            appendLine("Settings:")
            appendLine("- autoContextCompression=${settingRepository.getAutoContextCompression()}")
            appendLine("- appTestMode=${settingRepository.getAppTestMode()}")
            appendLine()
            appendLine("Recent app log:")
            appendLine(appLog.ifBlank { "(empty)" })
        }
    }

    fun addPlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.addPlatformV2(platform)
            fetchPlatforms()
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
            fetchPlatforms()
        }
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
            fetchPlatforms()
        }
    }

    fun togglePlatformEnabled(platformId: Int) {
        val platform = _platformState.value.find { it.id == platformId }
        platform?.let {
            updatePlatform(it.copy(enabled = !it.enabled))
        }
    }

    fun openThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = true) }

    fun closeThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = false) }

    fun openDeleteDialog(platformId: Int) = _dialogState.update {
        it.copy(
            isDeleteDialogOpen = true,
            platformToDelete = platformId
        )
    }

    fun closeDeleteDialog() = _dialogState.update {
        it.copy(
            isDeleteDialogOpen = false,
            platformToDelete = null
        )
    }

    fun confirmDelete() {
        _dialogState.value.platformToDelete?.let { platformId ->
            val platform = _platformState.value.find { it.id == platformId }
            platform?.let { deletePlatform(it) }
        }
        closeDeleteDialog()
    }

    data class DialogState(
        val isThemeDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false,
        val isTokenStatsDialogOpen: Boolean = false,
        val platformToDelete: Int? = null
    )
}
