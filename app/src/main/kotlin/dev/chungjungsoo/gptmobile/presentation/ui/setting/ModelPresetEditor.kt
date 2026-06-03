package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformModelPreset

@Composable
fun ModelPresetEditor(
    presets: List<PlatformModelPreset>,
    onPresetsChange: (List<PlatformModelPreset>) -> Unit,
    modifier: Modifier = Modifier,
    allowRemoveLast: Boolean = false
) {
    val editablePresets = presets.ifEmpty { listOf(PlatformModelPreset("", "")) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        editablePresets.forEachIndexed { index, preset ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.model_preset_index, index + 1),
                        modifier = Modifier.weight(1f)
                    )
                    val canRemove = editablePresets.size > 1 || allowRemoveLast
                    IconButton(
                        enabled = canRemove,
                        onClick = {
                            onPresetsChange(editablePresets.toMutableList().apply { removeAt(index) })
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.remove)
                        )
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = preset.model,
                    onValueChange = { value ->
                        onPresetsChange(
                            editablePresets.toMutableList().apply {
                                this[index] = preset.copy(model = value)
                            }
                        )
                    },
                    label = { Text(stringResource(R.string.model_name)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.model_supporting)) }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = preset.remark,
                    onValueChange = { value ->
                        onPresetsChange(
                            editablePresets.toMutableList().apply {
                                this[index] = preset.copy(remark = value)
                            }
                        )
                    },
                    label = { Text(stringResource(R.string.model_remark)) },
                    singleLine = true
                )
            }
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            onClick = {
                onPresetsChange(editablePresets + PlatformModelPreset("", ""))
            }
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Text(text = stringResource(R.string.add_model_preset), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

fun sanitizeModelPresets(presets: List<PlatformModelPreset>): List<PlatformModelPreset> {
    return presets
        .map { preset ->
            val model = preset.model.trim()
            PlatformModelPreset(
                model = model,
                remark = preset.remark.trim().ifBlank { model }
            )
        }
        .filter { it.model.isNotBlank() }
        .distinctBy { it.model }
}
