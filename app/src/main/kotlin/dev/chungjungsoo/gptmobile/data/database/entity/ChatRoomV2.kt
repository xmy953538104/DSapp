package dev.chungjungsoo.gptmobile.data.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.parcelize.Parcelize

const val DEFAULT_CHAT_GROUP_NAME = "默认"
const val CHAT_ICON_PROVIDER = "provider"
const val CHAT_ICON_LIFE = "life"
const val CHAT_ICON_WORK = "work"
const val CHAT_ICON_STUDY = "study"
const val CHAT_ICON_FOOD = "food"

@Parcelize
@Entity(tableName = "chats_v2")
data class ChatRoomV2(
    /**
     Now, enabled platforms are stored as list of strings.
     The strings are UUID V4 strings from PlatformV2.uid
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chat_id")
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "enabled_platform")
    val enabledPlatform: List<String>,

    @ColumnInfo(name = "group_name")
    val groupName: String = DEFAULT_CHAT_GROUP_NAME,

    @ColumnInfo(name = "icon")
    val icon: String = CHAT_ICON_PROVIDER,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000
) : Parcelable

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")

    @TypeConverter
    fun fromList(value: List<String>): String = if (value.isEmpty()) "" else value.joinToString(",")
}
