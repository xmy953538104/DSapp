package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.backup.AppBackupRepository
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.DEFAULT_CHAT_GROUP_NAME
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val appBackupRepository: AppBackupRepository
) : ViewModel() {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    data class ChatListState(
        val chats: List<ChatRoomV2> = listOf(),
        val isSelectionMode: Boolean = false,
        val isSearchMode: Boolean = false,
        val selectedPlatforms: List<Boolean> = listOf(),
        val selectedChats: List<Boolean> = listOf()
    )

    private val _chatListState = MutableStateFlow(ChatListState())
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState = _platformState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showSelectModelDialog = MutableStateFlow(false)
    val showSelectModelDialog: StateFlow<Boolean> = _showSelectModelDialog.asStateFlow()

    private val _showDeleteWarningDialog = MutableStateFlow(false)
    val showDeleteWarningDialog: StateFlow<Boolean> = _showDeleteWarningDialog.asStateFlow()

    private val _showMoveGroupDialog = MutableStateFlow(false)
    val showMoveGroupDialog: StateFlow<Boolean> = _showMoveGroupDialog.asStateFlow()

    init {
        // Set up debounced search
        _searchQuery
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { query -> searchChats(query) }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            appBackupRepository.syncWebDavIfDue()
        }
    }

    private var allChats: List<ChatRoomV2> = emptyList()

    private val _chatGroups = MutableStateFlow(listOf(DEFAULT_CHAT_GROUP_NAME))
    val chatGroups = _chatGroups.asStateFlow()

    private val _selectedGroup = MutableStateFlow(DEFAULT_CHAT_GROUP_NAME)
    val selectedGroup = _selectedGroup.asStateFlow()

    fun updatePlatformCheckedState(idx: Int) {
        if (idx < 0 || idx >= _chatListState.value.selectedPlatforms.size) return

        _chatListState.update {
            it.copy(
                selectedPlatforms = it.selectedPlatforms.mapIndexed { index, b ->
                    if (index == idx) {
                        !b
                    } else {
                        b
                    }
                }
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.update { query }
    }

    private fun searchChats(query: String) {
        viewModelScope.launch {
            allChats = chatRepository.searchChatsV2(query)
            updateVisibleChats()
        }
    }

    fun openDeleteWarningDialog() {
        closeSelectModelDialog()
        _showDeleteWarningDialog.update { true }
    }

    fun closeDeleteWarningDialog() {
        _showDeleteWarningDialog.update { false }
    }

    fun openMoveGroupDialog() {
        _showMoveGroupDialog.update { true }
    }

    fun closeMoveGroupDialog() {
        _showMoveGroupDialog.update { false }
    }

    fun openSelectModelDialog(preselectSingleEnabledPlatform: Boolean = false) {
        _showSelectModelDialog.update { true }
        if (preselectSingleEnabledPlatform) {
            val enabledCount = _platformState.value.count { it.enabled }
            if (enabledCount == 1) {
                _chatListState.update { state ->
                    state.copy(selectedPlatforms = _platformState.value.map { it.enabled })
                }
            }
        }
        disableSelectionMode()
    }

    fun closeSelectModelDialog() {
        _showSelectModelDialog.update { false }
        _chatListState.update { it.copy(selectedPlatforms = List(it.selectedPlatforms.size) { false }) }
    }

    fun deleteSelectedChats() {
        viewModelScope.launch {
            val selectedChats = _chatListState.value.chats.filterIndexed { index, _ ->
                _chatListState.value.selectedChats[index]
            }

            chatRepository.deleteChatsV2(selectedChats)
            allChats = chatRepository.fetchChatListV2()
            updateVisibleChats()
            disableSelectionMode()
        }
    }

    fun duplicateSelectedChat() {
        viewModelScope.launch {
            val selectedChats = _chatListState.value.chats.filterIndexed { index, _ ->
                _chatListState.value.selectedChats[index]
            }
            val selectedChat = selectedChats.singleOrNull() ?: return@launch

            chatRepository.duplicateChatV2(selectedChat)
            allChats = chatRepository.fetchChatListV2()
            updateVisibleChats()
            disableSelectionMode()
        }
    }

    fun moveSelectedChatsToGroup(groupName: String) {
        viewModelScope.launch {
            val selectedChats = _chatListState.value.chats.filterIndexed { index, _ ->
                _chatListState.value.selectedChats[index]
            }
            if (selectedChats.isEmpty()) return@launch

            chatRepository.updateChatsGroup(selectedChats, groupName)
            allChats = chatRepository.fetchChatListV2()
            updateVisibleChats()
            closeMoveGroupDialog()
            disableSelectionMode()
        }
    }

    fun disableSelectionMode() {
        _chatListState.update {
            it.copy(
                selectedChats = List(it.chats.size) { false },
                isSelectionMode = false
            )
        }
    }

    fun disableSearchMode() {
        _chatListState.update { it.copy(isSearchMode = false) }
        _searchQuery.update { "" }
    }

    fun enableSelectionMode() {
        disableSearchMode()
        _chatListState.update { it.copy(isSelectionMode = true) }
    }

    fun enableSearchMode() {
        disableSelectionMode()
        _chatListState.update { it.copy(isSearchMode = true) }
    }

    fun fetchChats() {
        viewModelScope.launch {
            allChats = chatRepository.fetchChatListV2()
            updateVisibleChats(resetSelection = true)

            Log.d("chats", "${_chatListState.value.chats}")
        }
    }

    fun fetchGroups() {
        viewModelScope.launch {
            val groups = settingRepository.getChatGroups()
            _chatGroups.update { groups }
            if (_selectedGroup.value !in groups) {
                _selectedGroup.update { groups.firstOrNull() ?: DEFAULT_CHAT_GROUP_NAME }
            }
            chatRepository.normalizeChatGroups(groups, groups.firstOrNull() ?: DEFAULT_CHAT_GROUP_NAME)
            allChats = chatRepository.fetchChatListV2()
            updateVisibleChats(resetSelection = true)
        }
    }

    fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatformV2s()
            _platformState.update { platforms }

            if (_chatListState.value.selectedPlatforms.size != platforms.size) {
                _chatListState.update { it.copy(selectedPlatforms = List(platforms.size) { false }) }
            }
        }
    }

    fun selectChat(chatRoomIdx: Int) {
        if (chatRoomIdx < 0 || chatRoomIdx >= _chatListState.value.chats.size) return

        _chatListState.update {
            it.copy(
                selectedChats = it.selectedChats.mapIndexed { index, b ->
                    if (index == chatRoomIdx) {
                        !b
                    } else {
                        b
                    }
                }
            )
        }

        if (_chatListState.value.selectedChats.count { it } == 0) {
            disableSelectionMode()
        }
    }

    fun selectGroup(groupName: String) {
        if (groupName !in _chatGroups.value) return
        _selectedGroup.update { groupName }
        disableSearchMode()
        updateVisibleChats(resetSelection = true)
    }

    private fun updateVisibleChats(resetSelection: Boolean = false) {
        val visibleChats = allChats.filter { chatRoom ->
            _chatGroups.value.size <= 1 || chatRoom.groupName == _selectedGroup.value
        }
        _chatListState.update {
            it.copy(
                chats = visibleChats,
                selectedChats = if (resetSelection || it.selectedChats.size != visibleChats.size) {
                    List(visibleChats.size) { false }
                } else {
                    it.selectedChats
                },
                isSelectionMode = if (resetSelection) false else it.isSelectionMode
            )
        }
    }
}
