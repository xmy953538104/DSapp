package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformModelPreset
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
    val openaiModels = linkedSetOf("gpt-5.2", "gpt-5-mini")
    val anthropicModels = linkedSetOf("claude-sonnet-4-5-20250929", "claude-3-5-haiku-20241022")
    val deepSeekModels = linkedSetOf("deepseek-v4-flash", "deepseek-v4-pro")
    val qwenModels = linkedSetOf("qwen3.7-flash", "qwen3.7-plus")

    const val OPENAI_API_URL = "https://api.openai.com/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/"
    const val DEEPSEEK_API_URL = "https://api.deepseek.com/"
    const val QWEN_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
    }

    fun getDefaultModel(clientType: ClientType): String = getDefaultModelPresets(clientType)
        .firstOrNull()
        ?.model
        .orEmpty()

    fun getDefaultModelPresets(clientType: ClientType): List<PlatformModelPreset> = when (clientType) {
        ClientType.OPENAI -> listOf(
            PlatformModelPreset("gpt-5-mini", DAILY_USE_REMARK),
            PlatformModelPreset("gpt-5.2", PROFESSIONAL_USE_REMARK)
        )

        ClientType.ANTHROPIC -> listOf(
            PlatformModelPreset("claude-3-5-haiku-20241022", DAILY_USE_REMARK),
            PlatformModelPreset("claude-sonnet-4-5-20250929", PROFESSIONAL_USE_REMARK)
        )

        ClientType.DEEPSEEK -> listOf(
            PlatformModelPreset("deepseek-v4-flash", DAILY_USE_REMARK),
            PlatformModelPreset("deepseek-v4-pro", PROFESSIONAL_USE_REMARK)
        )

        ClientType.QWEN -> listOf(
            PlatformModelPreset("qwen3.7-flash", DAILY_USE_REMARK),
            PlatformModelPreset("qwen3.7-plus", PROFESSIONAL_USE_REMARK)
        )

        ClientType.CUSTOM -> emptyList()
    }

    const val ANTHROPIC_MAXIMUM_TOKEN = 4096

    const val OPENAI_PROMPT =
        "You are a helpful, clever, and very friendly assistant. " +
            "You are familiar with various languages in the world. " +
            "You are to answer my questions precisely. "

    const val DEFAULT_PROMPT = "Your task is to answer my questions precisely."

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"

    private const val DAILY_USE_REMARK = "\u65e5\u5e38\u4f7f\u7528"
    private const val PROFESSIONAL_USE_REMARK = "\u4e13\u4e1a\u5e94\u7528"
}
