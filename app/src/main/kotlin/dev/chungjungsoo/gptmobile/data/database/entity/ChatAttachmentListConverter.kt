package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.TypeConverter
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatAttachmentListConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val supportedRemoteTypes = setOf("OPENAI_FILE", "ANTHROPIC_FILE")

    @TypeConverter
    fun fromString(value: String): List<ChatAttachment> = if (value.isBlank()) {
        emptyList()
    } else {
        runCatching {
            json.decodeFromString<List<ChatAttachment>>(value)
        }.getOrElse {
            runCatching {
                json.decodeFromString<List<ChatAttachment>>(sanitizeUnsupportedProviderRefs(value))
            }.getOrElse { emptyList() }
        }
    }

    @TypeConverter
    fun fromList(value: List<ChatAttachment>): String = json.encodeToString(value)

    private fun sanitizeUnsupportedProviderRefs(value: String): String {
        val root = json.parseToJsonElement(value).jsonArray
        return JsonArray(
            root.map { element ->
                val attachment = element.jsonObject
                val providerRefs = attachment["providerRefs"] as? JsonArray ?: return@map element
                val supportedProviderRefs = JsonArray(
                    providerRefs.filter { ref ->
                        val remoteType = (ref as? JsonObject)
                            ?.get("remoteType")
                            ?.jsonPrimitive
                            ?.content
                        remoteType in supportedRemoteTypes
                    }
                )
                JsonObject(attachment + ("providerRefs" to supportedProviderRefs))
            }
        ).toString()
    }
}
