package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.dao.ChatCompactionPointV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
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
                    reasoning = false
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

    override suspend fun addPlatformV2(platform: PlatformV2) {
        platformV2Dao.addPlatform(platform)
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        platformV2Dao.editPlatform(platform)
    }

    override suspend fun deletePlatformV2(platform: PlatformV2) {
        chatPlatformModelV2Dao.deleteByPlatformUid(platform.uid)
        chatCompactionPointV2Dao.deleteByPlatformUid(platform.uid)
        platformV2Dao.deletePlatform(platform)
    }

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = platformV2Dao.getPlatform(id)
        ?.takeIf { it.compatibleType in ClientType.SUPPORTED }
}
