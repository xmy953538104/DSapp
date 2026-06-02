package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_compaction_point_v2",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomV2::class,
            parentColumns = ["chat_id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["chat_id", "platform_uid"])
    ]
)
data class ChatCompactionPointV2(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "compaction_id")
    val id: Int = 0,

    @ColumnInfo(name = "chat_id")
    val chatId: Int,

    @ColumnInfo(name = "platform_uid")
    val platformUid: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "boundary_message_id")
    val boundaryMessageId: Int,

    @ColumnInfo(name = "tokens_before")
    val tokensBefore: Int,

    @ColumnInfo(name = "tokens_after")
    val tokensAfter: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)
