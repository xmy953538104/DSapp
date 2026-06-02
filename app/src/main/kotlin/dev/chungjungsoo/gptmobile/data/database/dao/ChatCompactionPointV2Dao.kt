package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.chungjungsoo.gptmobile.data.database.entity.ChatCompactionPointV2

@Dao
interface ChatCompactionPointV2Dao {

    @Query(
        "SELECT * FROM chat_compaction_point_v2 " +
            "WHERE chat_id = :chatId AND platform_uid = :platformUid " +
            "ORDER BY created_at DESC, compaction_id DESC LIMIT 1"
    )
    suspend fun getLatestForPlatform(chatId: Int, platformUid: String): ChatCompactionPointV2?

    @Query("SELECT * FROM chat_compaction_point_v2 WHERE chat_id = :chatId ORDER BY created_at DESC")
    suspend fun getByChatId(chatId: Int): List<ChatCompactionPointV2>

    @Query("SELECT * FROM chat_compaction_point_v2 ORDER BY created_at DESC")
    suspend fun getAll(): List<ChatCompactionPointV2>

    @Insert
    suspend fun addCompactionPoint(point: ChatCompactionPointV2): Long

    @Query("DELETE FROM chat_compaction_point_v2 WHERE chat_id = :chatId")
    suspend fun deleteByChatId(chatId: Int)

    @Query("DELETE FROM chat_compaction_point_v2 WHERE platform_uid = :platformUid")
    suspend fun deleteByPlatformUid(platformUid: String)
}
