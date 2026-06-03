package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.model.ApiType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
    val openaiModels = linkedSetOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4")
    val anthropicModels = linkedSetOf("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
    val deepSeekModels = linkedSetOf("deepseek-v4-flash", "deepseek-v4-pro")
    val qwenModels = linkedSetOf("qwen-vl-plus", "qwen-vl-max", "qwen3.5-plus", "qwen3-next-80b-a3b-thinking")

    const val OPENAI_API_URL = "https://api.openai.com/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/"
    const val DEEPSEEK_API_URL = "https://api.deepseek.com/"
    const val QWEN_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
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
}
