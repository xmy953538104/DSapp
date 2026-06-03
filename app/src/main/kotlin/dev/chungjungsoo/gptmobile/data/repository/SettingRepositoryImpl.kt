package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.dao.ChatCompactionPointV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.DEFAULT_CHAT_GROUP_NAME
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import javax.inject.Inject

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource,
    private val platformV2Dao: PlatformV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val chatCompactionPointV2Dao: ChatCompactionPointV2Dao
) : SettingRepository {

    override suspend fun fetchPlatforms(): List<Platform> = ApiType.entries.map { apiType ->
        val status = settingDataSource.getStatus(apiType)
        val apiUrl = when (apiType) {
            ApiType.OPENAI -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.OPENAI_API_URL
            ApiType.ANTHROPIC -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.ANTHROPIC_API_URL
        }
        val token = settingDataSource.getToken(apiType)
        val model = settingDataSource.getModel(apiType)
        val temperature = settingDataSource.getTemperature(apiType)
        val topP = settingDataSource.getTopP(apiType)
        val systemPrompt = when (apiType) {
            ApiType.OPENAI -> settingDataSource.getSystemPrompt(ApiType.OPENAI) ?: ModelConstants.OPENAI_PROMPT
            ApiType.ANTHROPIC -> settingDataSource.getSystemPrompt(ApiType.ANTHROPIC) ?: ModelConstants.DEFAULT_PROMPT
        }

        Platform(
            name = apiType,
            enabled = status == true,
            apiUrl = apiUrl,
            token = token,
            model = model,
            temperature = temperature,
            topP = topP,
            systemPrompt = systemPrompt
        )
    }

    override suspend fun fetchPlatformV2s(): List<PlatformV2> = platformV2Dao.getPlatforms()
        .filter { it.compatibleType in ClientType.SUPPORTED }
        .map { it.withDefaultModelPresets() }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting(
        dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
        themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
    )

    override suspend fun migrateToPlatformV2() {
        val leftOverPlatformV2s = platformV2Dao.getPlatforms()
        leftOverPlatformV2s.forEach { platformV2Dao.deletePlatform(it) }

        val platforms = fetchPlatforms().filter { platform ->
            platform.name == ApiType.OPENAI || platform.name == ApiType.ANTHROPIC
        }

        platforms.forEach { platform ->
            platformV2Dao.addPlatform(
                PlatformV2(
                    name = when (platform.name) {
                        ApiType.OPENAI -> "OpenAI"
                        ApiType.ANTHROPIC -> "Claude"
                    },
                    compatibleType = when (platform.name) {
                        ApiType.OPENAI -> ClientType.OPENAI
                        ApiType.ANTHROPIC -> ClientType.ANTHROPIC
                    },
                    enabled = platform.enabled,
                    apiUrl = if (
                        platform.name == ApiType.OPENAI &&
                        platform.apiUrl.endsWith("v1/")
                    ) {
                        platform.apiUrl.removeSuffix("v1/")
                    } else {
                        platform.apiUrl
                    },
                    token = platform.token,
                    model = platform.model ?: "",
                    temperature = platform.temperature,
                    topP = platform.topP,
                    systemPrompt = platform.systemPrompt,
                    stream = true,
                    reasoning = false,
                    modelPresets = ModelConstants.getDefaultModelPresets(
                        when (platform.name) {
                            ApiType.OPENAI -> ClientType.OPENAI
                            ApiType.ANTHROPIC -> ClientType.ANTHROPIC
                        }
                    )
                )
            )
        }
    }

    override suspend fun updatePlatforms(platforms: List<Platform>) {
        platforms.forEach { platform ->
            settingDataSource.updateStatus(platform.name, platform.enabled)
            settingDataSource.updateAPIUrl(platform.name, platform.apiUrl)

            platform.token?.let { settingDataSource.updateToken(platform.name, it) }
            platform.model?.let { settingDataSource.updateModel(platform.name, it) }
            platform.temperature?.let { settingDataSource.updateTemperature(platform.name, it) }
            platform.topP?.let { settingDataSource.updateTopP(platform.name, it) }
            platform.systemPrompt?.let { settingDataSource.updateSystemPrompt(platform.name, it.trim()) }
        }
    }

    override suspend fun updateThemes(themeSetting: ThemeSetting) {
        settingDataSource.updateDynamicTheme(themeSetting.dynamicTheme)
        settingDataSource.updateThemeMode(themeSetting.themeMode)
    }

    override suspend fun getAutoContextCompression(): Boolean =
        settingDataSource.getAutoContextCompression() ?: true

    override suspend fun updateAutoContextCompression(enabled: Boolean) {
        settingDataSource.updateAutoContextCompression(enabled)
    }

    override suspend fun getAppTestMode(): Boolean =
        settingDataSource.getAppTestMode() ?: false

    override suspend fun updateAppTestMode(enabled: Boolean) {
        settingDataSource.updateAppTestMode(enabled)
    }

    override suspend fun getChatGroups(): List<String> =
        normalizeChatGroups(settingDataSource.getChatGroups()?.lines().orEmpty())

    override suspend fun updateChatGroups(groups: List<String>) {
        val normalizedGroups = normalizeChatGroups(groups)
        settingDataSource.updateChatGroups(normalizedGroups.joinToString("\n"))
    }

    override suspend fun getWebDavConfig(): WebDavConfig = WebDavConfig(
        username = settingDataSource.getWebDavUsername() ?: "",
        url = settingDataSource.getWebDavUrl() ?: "",
        password = settingDataSource.getWebDavPassword() ?: "",
        readOnly = settingDataSource.getWebDavReadOnly() ?: false,
        lastSyncAt = settingDataSource.getWebDavLastSyncAt() ?: 0L
    )

    override suspend fun updateWebDavConfig(config: WebDavConfig) {
        settingDataSource.updateWebDavUsername(config.username.trim())
        settingDataSource.updateWebDavUrl(config.url.trim())
        settingDataSource.updateWebDavPassword(config.password)
        settingDataSource.updateWebDavReadOnly(config.readOnly)
        settingDataSource.updateWebDavLastSyncAt(config.lastSyncAt)
    }

    override suspend fun updateWebDavLastSyncAt(timestamp: Long) {
        settingDataSource.updateWebDavLastSyncAt(timestamp)
    }

    override suspend fun addPlatformV2(platform: PlatformV2) {
        platformV2Dao.addPlatform(platform.withDefaultModelPresets())
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        platformV2Dao.editPlatform(platform.withDefaultModelPresets())
    }

    override suspend fun deletePlatformV2(platform: PlatformV2) {
        chatPlatformModelV2Dao.deleteByPlatformUid(platform.uid)
        chatCompactionPointV2Dao.deleteByPlatformUid(platform.uid)
        platformV2Dao.deletePlatform(platform)
    }

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = platformV2Dao.getPlatform(id)
        ?.takeIf { it.compatibleType in ClientType.SUPPORTED }
        ?.withDefaultModelPresets()

    private fun PlatformV2.withDefaultModelPresets(): PlatformV2 {
        if (compatibleType == ClientType.CUSTOM) return this
        val defaults = ModelConstants.getDefaultModelPresets(compatibleType)
        if (defaults.isEmpty()) return this

        val normalizedPresets = modelPresets
            .map { preset ->
                preset.copy(
                    model = normalizeLegacyModel(preset.model.trim()),
                    remark = preset.remark.trim()
                )
            }
            .filter { it.model.isNotBlank() }
            .ifEmpty { defaults }

        val normalizedModel = model.takeIf { it.isNotBlank() }
            ?.let(::normalizeLegacyModel)
            ?.takeIf { normalized -> normalizedPresets.any { it.model == normalized } || modelPresets.isNotEmpty() }
            ?: normalizedPresets.first().model

        return copy(
            model = normalizedModel,
            modelPresets = normalizedPresets,
            reasoning = false.takeIf { reasoning && compatibleType == ClientType.CUSTOM } ?: reasoning
        )
    }

    private fun normalizeLegacyModel(model: String): String = when {
        model.equals("deepseek-chat", ignoreCase = true) -> "deepseek-v4-flash"
        model.equals("deepseek-reasoner", ignoreCase = true) -> "deepseek-v4-pro"
        model.equals("qwen-vl-plus", ignoreCase = true) -> "qwen3.6-flash"
        model.equals("qwen-vl-max", ignoreCase = true) -> "qwen3.7-plus"
        model.equals("qwen3.5-plus", ignoreCase = true) -> "qwen3.6-flash"
        model.equals("qwen3-next-80b-a3b-thinking", ignoreCase = true) -> "qwen3.7-plus"
        model.equals("qwen-flash", ignoreCase = true) -> "qwen3.6-flash"
        model.equals("qwen-plus", ignoreCase = true) -> "qwen3.7-plus"
        model.equals("qwen3.7-flash", ignoreCase = true) -> "qwen3.6-flash"
        else -> model
    }

    private fun normalizeChatGroups(groups: List<String>): List<String> {
        val sanitized = groups
            .map { it.trim().replace('\n', ' ').take(16) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_CHAT_GROUPS)
        return sanitized.ifEmpty { listOf(DEFAULT_CHAT_GROUP_NAME) }
    }

    private companion object {
        private const val MAX_CHAT_GROUPS = 5
    }
}
