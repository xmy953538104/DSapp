package dev.chungjungsoo.gptmobile.presentation.ui.startscreen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.backup.AppBackupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class StartViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appBackupRepository: AppBackupRepository
) : ViewModel() {
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() = _notice.update { null }

    fun importLocalBackup(context: Context, uri: Uri, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { appBackupRepository.importLocalBackup(context, uri) }
                .onSuccess {
                    _notice.update { appContext.getString(R.string.notice_backup_imported, it) }
                    onSuccess()
                }
                .onFailure { error ->
                    _notice.update { appContext.getString(R.string.notice_backup_import_failed, error.message ?: "unknown") }
                }
        }
    }

    fun importOwnerWebDav(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { appBackupRepository.downloadOwnerWebDavConfig(password) }
                .onSuccess {
                    _notice.update { appContext.getString(R.string.notice_cloud_config_pulled, it) }
                    onSuccess()
                }
                .onFailure { error ->
                    _notice.update { appContext.getString(R.string.notice_cloud_config_pull_failed, error.message ?: "unknown") }
                }
        }
    }
}
