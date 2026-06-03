package dev.chungjungsoo.gptmobile.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformModelPreset
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.WebDavConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppBackupRepository @Inject constructor(
    private val platformV2Dao: PlatformV2Dao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val settingRepository: SettingRepository,
    private val networkClient: NetworkClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportLocalBackup(context: Context): String = withContext(Dispatchers.IO) {
        val backup = AppBackup(
            exportedAt = System.currentTimeMillis() / 1000,
            chatGroups = settingRepository.getChatGroups(),
            platforms = platformV2Dao.getPlatforms().map(PlatformBackup::from),
            chats = chatRoomV2Dao.getChatRooms().map(ChatRoomBackup::from),
            messages = messageV2Dao.getAllMessages().map(MessageBackup::from),
            chatPlatformModels = chatPlatformModelV2Dao.getAll().map(ChatPlatformModelBackup::from)
        )
        val fileName = "Chat-AI-backup-${System.currentTimeMillis()}.json"
        writeDownloadFile(context, fileName, json.encodeToString(backup))
        fileName
    }

    suspend fun importLocalBackup(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        } ?: error("Cannot read backup file")
        val backup = json.decodeFromString<AppBackup>(raw)

        settingRepository.updateChatGroups(backup.chatGroups)
        restorePlatforms(backup.platforms)

        val chatIdMap = mutableMapOf<Int, Int>()
        backup.chats.forEach { chat ->
            val newId = chatRoomV2Dao.addChatRoom(chat.toEntity().copy(id = 0)).toInt()
            chatIdMap[chat.id] = newId
        }

        val messages = backup.messages.mapNotNull { message ->
            val newChatId = chatIdMap[message.chatId] ?: return@mapNotNull null
            message.toEntity(context, newChatId)
        }
        if (messages.isNotEmpty()) {
            messageV2Dao.addMessages(*messages.toTypedArray())
        }

        val modelRows = backup.chatPlatformModels.mapNotNull { model ->
            val newChatId = chatIdMap[model.chatId] ?: return@mapNotNull null
            model.toEntity(newChatId)
        }
        if (modelRows.isNotEmpty()) {
            chatPlatformModelV2Dao.upsertAll(*modelRows.toTypedArray())
        }

        chatIdMap.size
    }

    suspend fun uploadWebDavConfig() {
        val config = settingRepository.getWebDavConfig()
        uploadWebDavConfig(config)
    }

    suspend fun uploadWebDavConfig(config: WebDavConfig) {
        config.requireUsable()
        require(!config.readOnly) { "This WebDAV config is read-only" }
        val payload = WebDavProviderConfig(
            updatedAt = System.currentTimeMillis() / 1000,
            platforms = platformV2Dao.getPlatforms().map(WebDavPlatformBackup::from)
        )
        putWebDavJson(config, WEBDAV_CONFIG_FILE, json.encodeToString(payload))
        settingRepository.updateWebDavLastSyncAt(System.currentTimeMillis() / 1000)
    }

    suspend fun downloadWebDavConfig(): Int {
        val config = settingRepository.getWebDavConfig()
        return downloadWebDavConfig(config)
    }

    suspend fun downloadWebDavConfig(config: WebDavConfig): Int {
        config.requireUsable()
        val body = getWebDavJson(config, WEBDAV_CONFIG_FILE)
        val payload = json.decodeFromString<WebDavProviderConfig>(body)
        restoreWebDavPlatforms(payload.platforms)
        settingRepository.updateWebDavConfig(config.copy(lastSyncAt = System.currentTimeMillis() / 1000))
        return payload.platforms.size
    }

    suspend fun downloadOwnerWebDavConfig(password: String): Int {
        val current = settingRepository.getWebDavConfig()
        val config = current.copy(
            username = OWNER_WEBDAV_USERNAME,
            url = OWNER_WEBDAV_URL,
            password = password,
            readOnly = true
        )
        val count = downloadWebDavConfig(config)
        settingRepository.updateWebDavConfig(config.copy(lastSyncAt = System.currentTimeMillis() / 1000))
        return count
    }

    suspend fun syncWebDavIfDue() {
        val config = settingRepository.getWebDavConfig()
        if (config.password.isBlank()) return
        val now = System.currentTimeMillis() / 1000
        if (now - config.lastSyncAt < WEBDAV_SYNC_INTERVAL_SECONDS) return
        runCatching { downloadWebDavConfig(config) }
    }

    private suspend fun restorePlatforms(platforms: List<PlatformBackup>) {
        platforms.forEach { backup ->
            val existing = platformV2Dao.getPlatformByUid(backup.uid)
            val entity = backup.toEntity(existing?.id ?: 0)
            if (existing == null) {
                platformV2Dao.addPlatform(entity)
            } else {
                platformV2Dao.editPlatform(entity)
            }
        }
    }

    private suspend fun restoreWebDavPlatforms(platforms: List<WebDavPlatformBackup>) {
        platforms.forEach { backup ->
            val existing = platformV2Dao.getPlatformByUid(backup.uid)
                ?: platformV2Dao.getPlatforms().firstOrNull { it.compatibleType.name == backup.compatibleType && it.name == backup.name }
            val entity = backup.toEntity(existing)
            if (existing == null) {
                platformV2Dao.addPlatform(entity)
            } else {
                platformV2Dao.editPlatform(entity)
            }
        }
    }

    private fun writeDownloadFile(context: Context, fileName: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Cannot create download file")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot write download file")
    }

    private suspend fun putWebDavJson(config: WebDavConfig, fileName: String, body: String) {
        val response = networkClient().put(config.fileUrl(fileName)) {
            header(HttpHeaders.Authorization, config.basicAuthHeader())
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        check(response.status.isSuccess()) { "WebDAV upload failed: ${response.status}" }
    }

    private suspend fun getWebDavJson(config: WebDavConfig, fileName: String): String {
        val response = networkClient().get(config.fileUrl(fileName)) {
            header(HttpHeaders.Authorization, config.basicAuthHeader())
        }
        check(response.status.isSuccess()) { "WebDAV download failed: ${response.status}" }
        return response.bodyAsText()
    }

    private fun WebDavConfig.fileUrl(fileName: String): String = url.trimEnd('/') + "/" + fileName

    private fun WebDavConfig.requireUsable() {
        require(username.isNotBlank() && url.isNotBlank() && password.isNotBlank()) {
            "WebDAV settings are incomplete"
        }
    }

    private fun WebDavConfig.basicAuthHeader(): String {
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }

    private companion object {
        private const val WEBDAV_CONFIG_FILE = "chat-ai-provider-config.json"
        private const val WEBDAV_SYNC_INTERVAL_SECONDS = 7 * 24 * 60 * 60
        private const val OWNER_WEBDAV_USERNAME = "953538104@qq.com"
        private const val OWNER_WEBDAV_URL = "https://dav.jianguoyun.com/dav/Chat-AI"
    }
}

@Serializable
private data class AppBackup(
    val version: Int = 1,
    val exportedAt: Long,
    val chatGroups: List<String>,
    val platforms: List<PlatformBackup>,
    val chats: List<ChatRoomBackup>,
    val messages: List<MessageBackup>,
    val chatPlatformModels: List<ChatPlatformModelBackup>
)

@Serializable
private data class PlatformBackup(
    val uid: String,
    val name: String,
    val compatibleType: String,
    val enabled: Boolean,
    val apiUrl: String,
    val token: String?,
    val model: String,
    val temperature: Float?,
    val topP: Float?,
    val systemPrompt: String?,
    val stream: Boolean,
    val reasoning: Boolean,
    val modelPresets: List<PlatformModelPreset>,
    val timeout: Int
) {
    fun toEntity(id: Int): PlatformV2 = PlatformV2(
        id = id,
        uid = uid,
        name = name,
        compatibleType = ClientType.valueOf(compatibleType),
        enabled = enabled,
        apiUrl = apiUrl,
        token = token,
        model = model,
        temperature = temperature,
        topP = topP,
        systemPrompt = systemPrompt,
        stream = stream,
        reasoning = reasoning,
        modelPresets = modelPresets,
        timeout = timeout
    )

    companion object {
        fun from(platform: PlatformV2): PlatformBackup = PlatformBackup(
            uid = platform.uid,
            name = platform.name,
            compatibleType = platform.compatibleType.name,
            enabled = platform.enabled,
            apiUrl = platform.apiUrl,
            token = platform.token,
            model = platform.model,
            temperature = platform.temperature,
            topP = platform.topP,
            systemPrompt = platform.systemPrompt,
            stream = platform.stream,
            reasoning = platform.reasoning,
            modelPresets = platform.modelPresets,
            timeout = platform.timeout
        )
    }
}

@Serializable
private data class WebDavProviderConfig(
    val version: Int = 1,
    val updatedAt: Long,
    val platforms: List<WebDavPlatformBackup>
)

@Serializable
private data class WebDavPlatformBackup(
    val uid: String,
    val name: String,
    val compatibleType: String,
    val enabled: Boolean,
    val apiUrl: String,
    val token: String?,
    val model: String,
    val modelPresets: List<PlatformModelPreset>,
    val systemPrompt: String?,
    val stream: Boolean,
    val timeout: Int
) {
    fun toEntity(existing: PlatformV2?): PlatformV2 = PlatformV2(
        id = existing?.id ?: 0,
        uid = existing?.uid ?: uid,
        name = name,
        compatibleType = ClientType.valueOf(compatibleType),
        enabled = enabled,
        apiUrl = apiUrl,
        token = token,
        model = model,
        temperature = existing?.temperature,
        topP = existing?.topP,
        systemPrompt = systemPrompt,
        stream = stream,
        reasoning = false,
        modelPresets = modelPresets,
        timeout = timeout
    )

    companion object {
        fun from(platform: PlatformV2): WebDavPlatformBackup = WebDavPlatformBackup(
            uid = platform.uid,
            name = platform.name,
            compatibleType = platform.compatibleType.name,
            enabled = platform.enabled,
            apiUrl = platform.apiUrl,
            token = platform.token,
            model = platform.model,
            modelPresets = platform.modelPresets,
            systemPrompt = platform.systemPrompt,
            stream = platform.stream,
            timeout = platform.timeout
        )
    }
}

@Serializable
private data class ChatRoomBackup(
    val id: Int,
    val title: String,
    val enabledPlatform: List<String>,
    val groupName: String,
    val icon: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): ChatRoomV2 = ChatRoomV2(
        id = id,
        title = title,
        enabledPlatform = enabledPlatform,
        groupName = groupName,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(chatRoom: ChatRoomV2): ChatRoomBackup = ChatRoomBackup(
            id = chatRoom.id,
            title = chatRoom.title,
            enabledPlatform = chatRoom.enabledPlatform,
            groupName = chatRoom.groupName,
            icon = chatRoom.icon,
            createdAt = chatRoom.createdAt,
            updatedAt = chatRoom.updatedAt
        )
    }
}

@Serializable
private data class MessageBackup(
    val id: Int,
    val chatId: Int,
    val thoughts: String,
    val content: String,
    val attachments: List<ChatAttachment>,
    val attachmentFiles: List<BackupAttachmentFile> = emptyList(),
    val revisions: List<AssistantRevision>,
    val activeRevisionIndex: Int,
    val linkedMessageId: Int,
    val platformType: String?,
    val createdAt: Long
) {
    fun toEntity(context: Context, newChatId: Int): MessageV2 {
        val restoredFiles = attachmentFiles.associate { file ->
            file.originalPath to restoreAttachmentFile(context, file)
        }
        val restoredAttachments = attachments.map { attachment ->
            val restoredLocal = restoredFiles[attachment.localFilePath]
            val restoredPrepared = restoredFiles[attachment.preparedFilePath]
            attachment.copy(
                localFilePath = restoredLocal ?: restoredPrepared ?: attachment.localFilePath,
                preparedFilePath = restoredPrepared ?: restoredLocal ?: attachment.preparedFilePath,
                providerRefs = emptyList()
            )
        }

        return MessageV2(
            id = 0,
            chatId = newChatId,
            thoughts = thoughts,
            content = content,
            attachments = restoredAttachments,
            revisions = revisions,
            activeRevisionIndex = activeRevisionIndex,
            linkedMessageId = 0,
            platformType = platformType,
            createdAt = createdAt
        )
    }

    companion object {
        fun from(message: MessageV2): MessageBackup = MessageBackup(
            id = message.id,
            chatId = message.chatId,
            thoughts = message.thoughts,
            content = message.content,
            attachments = message.attachments,
            attachmentFiles = message.attachments.flatMap(::attachmentFilesForBackup).distinctBy { it.originalPath },
            revisions = message.revisions,
            activeRevisionIndex = message.activeRevisionIndex,
            linkedMessageId = message.linkedMessageId,
            platformType = message.platformType,
            createdAt = message.createdAt
        )

        private fun attachmentFilesForBackup(attachment: ChatAttachment): List<BackupAttachmentFile> =
            listOf(attachment.localFilePath, attachment.preparedFilePath)
                .filter { it.isNotBlank() }
                .distinct()
                .mapNotNull { path ->
                    val file = File(path)
                    if (!file.exists() || !file.isFile) return@mapNotNull null
                    BackupAttachmentFile(
                        originalPath = path,
                        displayName = attachment.resolvedDisplayName,
                        mimeType = attachment.mimeType,
                        base64 = Base64.getEncoder().encodeToString(file.readBytes())
                    )
                }

        private fun restoreAttachmentFile(context: Context, backupFile: BackupAttachmentFile): String {
            val attachmentsDir = File(context.filesDir, "attachments")
            attachmentsDir.mkdirs()
            val safeName = sanitizeFileName(backupFile.displayName.ifBlank { File(backupFile.originalPath).name })
            val pathHash = backupFile.originalPath.hashCode().toString().replace("-", "m")
            val targetFile = File(attachmentsDir, "backup_${System.currentTimeMillis()}_${pathHash}_$safeName")
            targetFile.writeBytes(Base64.getDecoder().decode(backupFile.base64))
            return targetFile.absolutePath
        }

        private fun sanitizeFileName(fileName: String): String {
            val sanitized = fileName
                .replace("..", "")
                .replace("/", "")
                .replace("\\", "")
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
                .take(160)
                .trim('.')
            return sanitized.ifBlank { "attachment" }
        }
    }
}

@Serializable
private data class BackupAttachmentFile(
    val originalPath: String,
    val displayName: String,
    val mimeType: String,
    val base64: String
)

@Serializable
private data class ChatPlatformModelBackup(
    val chatId: Int,
    val platformUid: String,
    val model: String,
    val reasoning: Boolean,
    val updatedAt: Long
) {
    fun toEntity(newChatId: Int): ChatPlatformModelV2 = ChatPlatformModelV2(
        chatId = newChatId,
        platformUid = platformUid,
        model = model,
        reasoning = reasoning,
        updatedAt = updatedAt
    )

    companion object {
        fun from(model: ChatPlatformModelV2): ChatPlatformModelBackup = ChatPlatformModelBackup(
            chatId = model.chatId,
            platformUid = model.platformUid,
            model = model.model,
            reasoning = model.reasoning,
            updatedAt = model.updatedAt
        )
    }
}
