package dev.chungjungsoo.gptmobile.data.model

enum class ClientType {
    OPENAI,
    ANTHROPIC,
    DEEPSEEK,
    CUSTOM;

    companion object {
        val USER_SELECTABLE = listOf(OPENAI, ANTHROPIC, DEEPSEEK, CUSTOM)
        val SUPPORTED = USER_SELECTABLE.toSet()
    }
}
