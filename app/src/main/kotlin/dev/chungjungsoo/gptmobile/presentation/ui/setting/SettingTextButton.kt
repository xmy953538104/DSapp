package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chungjungsoo.gptmobile.presentation.common.NeutralTextButton

@Composable
fun SettingTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    NeutralTextButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        content = content
    )
}
