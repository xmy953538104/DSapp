package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class PlatformModelPreset(
    val model: String,
    val remark: String
)

@Entity(tableName = "platform_v2")
data class PlatformV2(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("platform_id")
    val id: Int = 0,

    @ColumnInfo("uid")
    val uid: String = UUID.randomUUID().toString(),

    @ColumnInfo("name")
    val name: String,

    @ColumnInfo("compatible_type")
    val compatibleType: ClientType,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = false,

    @ColumnInfo(name = "api_url")
    val apiUrl: String,

    @ColumnInfo(name = "token")
    val token: String? = null,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "temperature")
    val temperature: Float? = null,

    @ColumnInfo(name = "top_p")
    val topP: Float? = null,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "stream")
    val stream: Boolean = true,

    @ColumnInfo(name = "reasoning")
    val reasoning: Boolean = false,

    @ColumnInfo(name = "model_presets")
    val modelPresets: List<PlatformModelPreset> = emptyList(),

    @ColumnInfo(name = "timeout")
    val timeout: Int = 30
)

class PlatformModelPresetListConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromString(value: String?): List<PlatformModelPreset> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PlatformModelPreset.serializer()), value)
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromList(value: List<PlatformModelPreset>?): String {
        return json.encodeToString(ListSerializer(PlatformModelPreset.serializer()), value.orEmpty())
    }
}
