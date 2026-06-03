package dev.chungjungsoo.gptmobile.presentation.common

import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType

fun providerIconResId(clientType: ClientType): Int? = when (clientType) {
    ClientType.OPENAI -> R.drawable.provider_openai
    ClientType.ANTHROPIC -> R.drawable.provider_claude
    ClientType.DEEPSEEK -> R.drawable.provider_deepseek
    ClientType.QWEN -> R.drawable.provider_qwen
    ClientType.CUSTOM -> null
}
