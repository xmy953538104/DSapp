package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import kotlin.math.ceil

private const val IMAGE_ATTACHMENT_TOKEN_ESTIMATE = 85

data class ChatTokenStats(
    val chatId: Int,
    val title: String,
    val messageCount: Int,
    val estimatedTokens: Int,
    val compactedTokensSaved: Int
)

data class TokenUsageStats(
    val totalChats: Int,
    val totalMessages: Int,
    val totalEstimatedTokens: Int,
    val totalCompactedTokensSaved: Int,
    val chats: List<ChatTokenStats>
)

fun estimateTextTokens(text: String): Int {
    if (text.isBlank()) return 0

    var asciiLikeChars = 0
    var cjkChars = 0
    text.forEach { char ->
        when {
            char.isWhitespace() -> {}
            char.isCjkLike() -> cjkChars += 1
            else -> asciiLikeChars += 1
        }
    }

    return cjkChars + ceil(asciiLikeChars / 4.0).toInt()
}

fun estimateMessageTokens(message: MessageV2): Int {
    val textTokens = estimateTextTokens(message.effectiveContent()) + estimateTextTokens(message.effectiveThoughts())
    val attachmentTokens = message.attachments.size * IMAGE_ATTACHMENT_TOKEN_ESTIMATE
    return textTokens + attachmentTokens + 4
}

fun estimateMessagesTokens(messages: List<MessageV2>): Int = messages.sumOf(::estimateMessageTokens)

private fun Char.isCjkLike(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
        block == Character.UnicodeBlock.HANGUL_JAMO ||
        block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
}
