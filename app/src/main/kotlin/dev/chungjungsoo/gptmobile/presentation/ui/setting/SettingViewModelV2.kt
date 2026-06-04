package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.backup.AppBackupRepository
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.WebDavConfig
import dev.chungjungsoo.gptmobile.util.TokenUsageStats
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingViewModelV2 @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository,
    private val appBackupRepository: AppBackupRepository
) : ViewModel() {

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState: StateFlow<List<PlatformV2>> = _platformState.asStateFlow()

    private val _autoContextCompression = MutableStateFlow(true)
    val autoContextCompression: StateFlow<Boolean> = _autoContextCompression.asStateFlow()

    private val _appTestMode = MutableStateFlow(false)
    val appTestMode: StateFlow<Boolean> = _appTestMode.asStateFlow()

    private val _tokenUsageStats = MutableStateFlow<TokenUsageStats?>(null)
    val tokenUsageStats: StateFlow<TokenUsageStats?> = _tokenUsageStats.asStateFlow()

    private val _chatGroups = MutableStateFlow(listOf<String>())
    val chatGroups: StateFlow<List<String>> = _chatGroups.asStateFlow()

    private val _webDavConfig = MutableStateFlow(WebDavConfig(username = "", url = "", password = "", readOnly = false, lastSyncAt = 0L))
    val webDavConfig: StateFlow<WebDavConfig> = _webDavConfig.asStateFlow()

    private val _operationNotice = MutableStateFlow<String?>(null)
    val operationNotice: StateFlow<String?> = _operationNotice.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    init {
        fetchPlatforms()
        fetchAutoContextCompression()
        fetchAppTestMode()
        fetchChatGroups()
        fetchWebDavConfig()
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

    fun consumeOperationNotice() = _operationNotice.update { null }

    fun fetchChatGroups() {
        viewModelScope.launch {
            _chatGroups.update { settingRepository.getChatGroups() }
        }
    }

    fun openChatGroupDialog() = _dialogState.update { it.copy(isChatGroupDialogOpen = true) }

    fun closeChatGroupDialog() = _dialogState.update { it.copy(isChatGroupDialogOpen = false) }

    fun openBackupDialog() = _dialogState.update { it.copy(isBackupDialogOpen = true) }

    fun closeBackupDialog() = _dialogState.update { it.copy(isBackupDialogOpen = false) }

    fun updateChatGroups(groups: List<String>) {
        viewModelScope.launch {
            val normalized = groups
                .map { it.trim().replace('\n', ' ').take(16) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_CHAT_GROUPS)
            settingRepository.updateChatGroups(normalized)
            val savedGroups = settingRepository.getChatGroups()
            chatRepository.normalizeChatGroups(savedGroups, savedGroups.first())
            _chatGroups.update { savedGroups }
            closeChatGroupDialog()
        }
    }

    fun fetchWebDavConfig() {
        viewModelScope.launch {
            _webDavConfig.update { settingRepository.getWebDavConfig() }
        }
    }

    fun updateWebDavConfig(username: String, url: String, password: String) {
        viewModelScope.launch {
            saveWebDavConfig(username, url, password)
            _operationNotice.update { appContext.getString(R.string.notice_webdav_saved) }
        }
    }

    fun clearWebDavConfig() {
        viewModelScope.launch {
            settingRepository.updateWebDavConfig(
                WebDavConfig(username = "", url = "", password = "", readOnly = false, lastSyncAt = 0L)
            )
            _webDavConfig.update { settingRepository.getWebDavConfig() }
            _operationNotice.update { appContext.getString(R.string.notice_webdav_cleared) }
        }
    }

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

    fun exportLocalBackup(context: Context) {
        viewModelScope.launch {
            val notice = runCatching { appBackupRepository.exportLocalBackup(context) }
                .fold(
                    onSuccess = { appContext.getString(R.string.notice_backup_exported, it) },
                    onFailure = { appContext.getString(R.string.notice_backup_export_failed, it.message ?: "unknown") }
                )
            _operationNotice.update { notice }
        }
    }

    fun importLocalBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val notice = runCatching { appBackupRepository.importLocalBackup(context, uri) }
                .fold(
                    onSuccess = { appContext.getString(R.string.notice_backup_imported, it) },
                    onFailure = { appContext.getString(R.string.notice_backup_import_failed, it.message ?: "unknown") }
                )
            fetchPlatforms()
            fetchChatGroups()
            _operationNotice.update { notice }
        }
    }

    fun uploadWebDavConfig() {
        viewModelScope.launch {
            val notice = runCatching { appBackupRepository.uploadWebDavConfig() }
                .fold(
                    onSuccess = { appContext.getString(R.string.notice_webdav_uploaded) },
                    onFailure = { appContext.getString(R.string.notice_webdav_upload_failed, it.message ?: "unknown") }
                )
            _operationNotice.update { notice }
        }
    }

    fun uploadWebDavConfig(username: String, url: String, password: String) {
        viewModelScope.launch {
            val config = saveWebDavConfig(username, url, password)
            val notice = runCatching { appBackupRepository.uploadWebDavConfig(config) }
                .fold(
                    onSuccess = { appContext.getString(R.string.notice_webdav_uploaded) },
                    onFailure = { appContext.getString(R.string.notice_webdav_upload_failed, it.message ?: "unknown") }
                )
            fetchWebDavConfig()
            _operationNotice.update { notice }
        }
    }

    fun downloadWebDavConfig() {
        viewModelScope.launch {
            val notice = runCatching { appBackupRepository.downloadWebDavConfig() }
                .fold(
                    onSuccess = { appContext.getString(R.string.notice_webdav_pulled, it) },
                    onFailure = { appContext.getString(R.string.notice_webdav_pull_failed, it.message ?: "unknown") }
                )
            fetchPlatforms()
            _operationNotice.update { notice }
        }
    }

    fun downloadWebDavConfig(username: String, url: String, password: String) {
        viewModelScope.launch {
            val config = saveWebDavConfig(username, url, password)
            val notice = runCatching { appBackupRepository.downloadWebDavConfig(config) }
                .fold(
                    onSuccess = { appContext.getString(R.string.notice_webdav_pulled, it) },
                    onFailure = { appContext.getString(R.string.notice_webdav_pull_failed, it.message ?: "unknown") }
                )
            fetchPlatforms()
            fetchWebDavConfig()
            _operationNotice.update { notice }
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
        val isChatGroupDialogOpen: Boolean = false,
        val isBackupDialogOpen: Boolean = false,
        val platformToDelete: Int? = null
    )

    private suspend fun saveWebDavConfig(username: String, url: String, password: String): WebDavConfig {
        val previous = _webDavConfig.value
        val trimmedUsername = username.trim()
        val trimmedUrl = url.trim()
        val keepReadOnly = previous.readOnly && previous.username == trimmedUsername && previous.url == trimmedUrl
        val config = previous.copy(
            username = trimmedUsername,
            url = trimmedUrl,
            password = password,
            readOnly = keepReadOnly
        )
        settingRepository.updateWebDavConfig(config)
        val savedConfig = settingRepository.getWebDavConfig()
        _webDavConfig.update { savedConfig }
        return savedConfig
    }

    private companion object {
        private const val MAX_CHAT_GROUPS = 5
    }
}
