package dev.chungjungsoo.gptmobile.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.io.File

object ChatDatabaseV2Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_platform_model_v2` (
                    `chat_id` INTEGER NOT NULL,
                    `platform_uid` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`chat_id`, `platform_uid`),
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            val platformModelMap = mutableMapOf<String, String>()
            db.query("SELECT uid, model FROM platform_v2").use { platformCursor ->
                val uidIndex = platformCursor.getColumnIndexOrThrow("uid")
                val modelIndex = platformCursor.getColumnIndexOrThrow("model")
                while (platformCursor.moveToNext()) {
                    val uid = platformCursor.getString(uidIndex)
                    val model = platformCursor.getString(modelIndex) ?: ""
                    platformModelMap[uid] = model
                }
            }

            val currentTimestamp = System.currentTimeMillis() / 1000
            db.query("SELECT chat_id, enabled_platform FROM chats_v2").use { chatCursor ->
                val chatIdIndex = chatCursor.getColumnIndexOrThrow("chat_id")
                val enabledPlatformIndex = chatCursor.getColumnIndexOrThrow("enabled_platform")
                while (chatCursor.moveToNext()) {
                    val chatId = chatCursor.getInt(chatIdIndex)
                    val enabledPlatform = chatCursor.getString(enabledPlatformIndex) ?: ""
                    if (enabledPlatform.isBlank()) continue

                    enabledPlatform
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { platformUid ->
                            val model = platformModelMap[platformUid] ?: ""
                            db.execSQL(
                                "INSERT OR REPLACE INTO chat_platform_model_v2 (chat_id, platform_uid, model, updated_at) VALUES (?, ?, ?, ?)",
                                arrayOf<Any>(chatId, platformUid, model, currentTimestamp)
                            )
                        }
                }
            }
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages_v2_new` (
                    `message_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `thoughts` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `attachments` TEXT NOT NULL,
                    `revisions` TEXT NOT NULL,
                    `linked_message_id` INTEGER NOT NULL,
                    `platform_type` TEXT,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO `messages_v2_new` (
                    `message_id`,
                    `chat_id`,
                    `thoughts`,
                    `content`,
                    `attachments`,
                    `revisions`,
                    `linked_message_id`,
                    `platform_type`,
                    `created_at`
                )
                SELECT
                    `message_id`,
                    `chat_id`,
                    `thoughts`,
                    `content`,
                    '' as `attachments`,
                    `revisions`,
                    `linked_message_id`,
                    `platform_type`,
                    `created_at`
                FROM `messages_v2`
                """.trimIndent()
            )

            db.query("SELECT message_id, files FROM messages_v2").use { messageCursor ->
                val messageIdIndex = messageCursor.getColumnIndexOrThrow("message_id")
                val filesIndex = messageCursor.getColumnIndexOrThrow("files")
                while (messageCursor.moveToNext()) {
                    val messageId = messageCursor.getInt(messageIdIndex)
                    val filesValue = messageCursor.getString(filesIndex).orEmpty()
                    db.execSQL(
                        "UPDATE messages_v2_new SET attachments = ? WHERE message_id = ?",
                        arrayOf<Any>(legacyFilesToAttachmentsJson(filesValue), messageId)
                    )
                }
            }

            db.execSQL("DROP TABLE `messages_v2`")
            db.execSQL("ALTER TABLE `messages_v2_new` RENAME TO `messages_v2`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_v2_chat_id` ON `messages_v2` (`chat_id`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages_v2_new` (
                    `message_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `thoughts` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `attachments` TEXT NOT NULL,
                    `revisions` TEXT NOT NULL,
                    `active_revision_index` INTEGER NOT NULL,
                    `linked_message_id` INTEGER NOT NULL,
                    `platform_type` TEXT,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.query(
                """
                SELECT
                    `message_id`,
                    `chat_id`,
                    `thoughts`,
                    `content`,
                    `attachments`,
                    `revisions`,
                    `linked_message_id`,
                    `platform_type`,
                    `created_at`
                FROM `messages_v2`
                """.trimIndent()
            ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("message_id")
                val chatIdIndex = cursor.getColumnIndexOrThrow("chat_id")
                val thoughtsIndex = cursor.getColumnIndexOrThrow("thoughts")
                val contentIndex = cursor.getColumnIndexOrThrow("content")
                val attachmentsIndex = cursor.getColumnIndexOrThrow("attachments")
                val revisionsIndex = cursor.getColumnIndexOrThrow("revisions")
                val linkedMessageIdIndex = cursor.getColumnIndexOrThrow("linked_message_id")
                val platformTypeIndex = cursor.getColumnIndexOrThrow("platform_type")
                val createdAtIndex = cursor.getColumnIndexOrThrow("created_at")

                while (cursor.moveToNext()) {
                    db.execSQL(
                        """
                        INSERT INTO `messages_v2_new` (
                            `message_id`,
                            `chat_id`,
                            `thoughts`,
                            `content`,
                            `attachments`,
                            `revisions`,
                            `active_revision_index`,
                            `linked_message_id`,
                            `platform_type`,
                            `created_at`
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            cursor.getInt(idIndex),
                            cursor.getInt(chatIdIndex),
                            cursor.getString(thoughtsIndex) ?: "",
                            cursor.getString(contentIndex) ?: "",
                            cursor.getString(attachmentsIndex) ?: "",
                            legacyRevisionsToStructuredJson(
                                revisionsValue = cursor.getString(revisionsIndex).orEmpty(),
                                createdAt = cursor.getLong(createdAtIndex)
                            ),
                            ACTIVE_REVISION_LATEST,
                            cursor.getInt(linkedMessageIdIndex),
                            cursor.getString(platformTypeIndex),
                            cursor.getLong(createdAtIndex)
                        )
                    )
                }
            }

            db.execSQL("DROP TABLE `messages_v2`")
            db.execSQL("ALTER TABLE `messages_v2_new` RENAME TO `messages_v2`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_v2_chat_id` ON `messages_v2` (`chat_id`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val removedPlatformUids = mutableSetOf<String>()
            db.query(
                "SELECT `uid` FROM `platform_v2` " +
                    "WHERE `compatible_type` NOT IN ('OPENAI', 'ANTHROPIC', 'DEEPSEEK', 'QWEN', 'CUSTOM')"
            ).use { cursor ->
                val uidIndex = cursor.getColumnIndex("uid")
                while (cursor.moveToNext()) {
                    removedPlatformUids += cursor.getString(uidIndex)
                }
            }

            if (removedPlatformUids.isNotEmpty()) {
                val removedPlatformUidArgs = removedPlatformUids.map { it as Any }.toTypedArray()
                db.query("SELECT `chat_id`, `enabled_platform` FROM `chats_v2`").use { cursor ->
                    val chatIdIndex = cursor.getColumnIndex("chat_id")
                    val enabledPlatformIndex = cursor.getColumnIndex("enabled_platform")
                    while (cursor.moveToNext()) {
                        val chatId = cursor.getInt(chatIdIndex)
                        val enabledPlatforms = cursor.getString(enabledPlatformIndex)
                            .split(",")
                            .filter { it.isNotBlank() && it !in removedPlatformUids }
                            .joinToString(",")
                        db.execSQL(
                            "UPDATE `chats_v2` SET `enabled_platform` = ? WHERE `chat_id` = ?",
                            arrayOf<Any?>(enabledPlatforms, chatId)
                        )
                    }
                }

                db.execSQL(
                    "DELETE FROM `chat_platform_model_v2` WHERE `platform_uid` IN (" +
                        removedPlatformUids.joinToString(",") { "?" } +
                        ")",
                    removedPlatformUidArgs
                )
                db.execSQL(
                    "DELETE FROM `platform_v2` WHERE `uid` IN (" +
                        removedPlatformUids.joinToString(",") { "?" } +
                        ")",
                    removedPlatformUidArgs
                )
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_compaction_point_v2` (
                    `compaction_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `platform_uid` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `boundary_message_id` INTEGER NOT NULL,
                    `tokens_before` INTEGER NOT NULL,
                    `tokens_after` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_compaction_point_v2_chat_id` ON `chat_compaction_point_v2` (`chat_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_compaction_point_v2_chat_id_platform_uid` " +
                    "ON `chat_compaction_point_v2` (`chat_id`, `platform_uid`)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `platform_v2` ADD COLUMN `model_presets` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    internal fun legacyFilesToAttachmentsJson(filesValue: String): String {
        val attachments = filesValue
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { filePath ->
                ChatAttachment(
                    localFilePath = filePath,
                    preparedFilePath = filePath,
                    displayName = File(filePath).name,
                    mimeType = "",
                    sizeBytes = 0L
                )
            }

        return ChatAttachmentListConverter().fromList(attachments)
    }

    internal fun legacyRevisionsToStructuredJson(
        revisionsValue: String,
        createdAt: Long
    ): String {
        val revisions = revisionsValue
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { AssistantRevision(content = it, thoughts = "", createdAt = createdAt) }

        return AssistantRevisionListConverter().fromList(revisions)
    }
}
