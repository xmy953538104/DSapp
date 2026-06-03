package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting

interface SettingRepository {
    suspend fun fetchPlatforms(): List<Platform>
    suspend fun fetchPlatformV2s(): List<PlatformV2>
    suspend fun fetchThemes(): ThemeSetting
    suspend fun migrateToPlatformV2()
    suspend fun updatePlatforms(platforms: List<Platform>)
    suspend fun updateThemes(themeSetting: ThemeSetting)
    suspend fun getAutoContextCompression(): Boolean
    suspend fun updateAutoContextCompression(enabled: Boolean)
    suspend fun getAppTestMode(): Boolean
    suspend fun updateAppTestMode(enabled: Boolean)
    suspend fun getChatGroups(): List<String>
    suspend fun updateChatGroups(groups: List<String>)
    suspend fun getWebDavConfig(): WebDavConfig
    suspend fun updateWebDavConfig(config: WebDavConfig)
    suspend fun updateWebDavLastSyncAt(timestamp: Long)

    // PlatformV2 CRUD operations
    suspend fun addPlatformV2(platform: PlatformV2)
    suspend fun updatePlatformV2(platform: PlatformV2)
    suspend fun deletePlatformV2(platform: PlatformV2)
    suspend fun getPlatformV2ById(id: Int): PlatformV2?
}

data class WebDavConfig(
    val username: String,
    val url: String,
    val password: String,
    val readOnly: Boolean,
    val lastSyncAt: Long
)
