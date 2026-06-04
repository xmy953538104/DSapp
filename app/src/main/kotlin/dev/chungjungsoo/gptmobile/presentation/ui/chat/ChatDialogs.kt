package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.CHAT_ICON_FOOD
import dev.chungjungsoo.gptmobile.data.database.entity.CHAT_ICON_LIFE
import dev.chungjungsoo.gptmobile.data.database.entity.CHAT_ICON_PLAY
import dev.chungjungsoo.gptmobile.data.database.entity.CHAT_ICON_PROVIDER
import dev.chungjungsoo.gptmobile.data.database.entity.CHAT_ICON_STUDY
import dev.chungjungsoo.gptmobile.data.database.entity.CHAT_ICON_WORK
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformModelPreset
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import dev.chungjungsoo.gptmobile.data.model.ClientType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatModelDialog(
    platformOrder: List<String>,
    initialModels: Map<String, String>,
    platformNames: Map<String, String>,
    platformTypes: Map<String, ClientType> = emptyMap(),
    platformModelPresets: Map<String, List<PlatformModelPreset>> = emptyMap(),
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Map<String, String>) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var models by rememberSaveable(platformOrder, initialModels, platformTypes, platformModelPresets) {
        mutableStateOf(
            platformOrder.associateWith { uid ->
                val initialModel = initialModels[uid].orEmpty()
                normalizePickerModel(platformTypes[uid] ?: ClientType.CUSTOM, initialModel)
            }
        )
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.chat_models)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.chat_models_description),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                platformOrder.forEach { platformUid ->
                    val platformName = platformNames[platformUid] ?: stringResource(R.string.unknown)
                    val presets = platformModelPresets[platformUid].orEmpty()
                    if (presets.isNotEmpty()) {
                        ProviderModelPicker(
                            platformName = platformName,
                            selectedModel = models[platformUid].orEmpty(),
                            presets = presets,
                            onModelChange = { model ->
                                models = models.toMutableMap().apply { put(platformUid, model) }
                            }
                        )
                    } else {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            value = models[platformUid].orEmpty(),
                            onValueChange = { value ->
                                models = models.toMutableMap().apply { put(platformUid, value) }
                            },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.chat_model_for_platform, platformName)) },
                            supportingText = {
                                Text(stringResource(R.string.model_supporting))
                            }
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasBlank = platformOrder.any { models[it].orEmpty().trim().isBlank() }
            TextButton(
                enabled = !hasBlank,
                onClick = {
                    onConfirmRequest(
                        models.mapValues { (_, model) -> model.trim() }
                    )
                }
            ) {
                Text(stringResource(R.string.update_chat_models))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ProviderModelPicker(
    platformName: String,
    selectedModel: String,
    presets: List<PlatformModelPreset>,
    onModelChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.chat_model_for_platform, platformName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        presets.forEach { preset ->
            ProviderModelOptionCard(
                title = preset.remark.ifBlank { preset.model },
                model = preset.model,
                selected = selectedModel.equals(preset.model, ignoreCase = true),
                onModelChange = onModelChange
            )
        }
    }
}

private fun normalizePickerModel(clientType: ClientType, model: String): String = when {
    clientType == ClientType.DEEPSEEK && model.equals("deepseek-reasoner", ignoreCase = true) -> "deepseek-v4-pro"
    clientType == ClientType.DEEPSEEK && model.equals("deepseek-chat", ignoreCase = true) -> "deepseek-v4-flash"
    clientType == ClientType.QWEN && model.equals("qwen-vl-plus", ignoreCase = true) -> "qwen3.7-plus"
    clientType == ClientType.QWEN && model.equals("qwen-vl-max", ignoreCase = true) -> "qwen3.7-max"
    clientType == ClientType.QWEN && model.equals("qwen3.5-plus", ignoreCase = true) -> "qwen3.7-plus"
    clientType == ClientType.QWEN && model.equals("qwen3-next-80b-a3b-thinking", ignoreCase = true) -> "qwen3.7-max"
    clientType == ClientType.QWEN && model.equals("qwen-flash", ignoreCase = true) -> "qwen3.7-plus"
    clientType == ClientType.QWEN && model.equals("qwen-plus", ignoreCase = true) -> "qwen3.7-plus"
    clientType == ClientType.QWEN && model.equals("qwen3.7-flash", ignoreCase = true) -> "qwen3.7-plus"
    clientType == ClientType.QWEN && model.equals("qwen-3.7-plus", ignoreCase = true) -> "qwen3.7-plus"
    clientType == ClientType.QWEN && model.equals("qwen-3.7-max", ignoreCase = true) -> "qwen3.7-max"
    else -> model
}

@Composable
private fun ProviderModelOptionCard(
    title: String,
    model: String,
    selected: Boolean,
    onModelChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onModelChange(model) },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = { onModelChange(model) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatTitleDialog(
    initialTitle: String,
    initialIcon: String,
    onConfirmRequest: (title: String, icon: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var selectedIcon by rememberSaveable { mutableStateOf(initialIcon) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = stringResource(R.string.chat_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    value = title,
                    singleLine = true,
                    isError = title.length > 50,
                    supportingText = {
                        if (title.length > 50) {
                            Text(stringResource(R.string.title_length_limit, title.length))
                        }
                    },
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.chat_title)) }
                )
                Text(
                    text = stringResource(R.string.chat_icon),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    chatIconOptions().forEach { option ->
                        ChatIconCircleButton(
                            option = option,
                            selected = selectedIcon == option.id,
                            onClick = { selectedIcon = option.id }
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (title != initialTitle || selectedIcon != initialIcon),
                onClick = {
                    onConfirmRequest(title, selectedIcon)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.update))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { selectedIcon = CHAT_ICON_PROVIDER }
                ) {
                    Text(text = stringResource(R.string.default_mode))
                }
                TextButton(
                    onClick = onDismissRequest
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private data class ChatIconOption(
    val id: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val backgroundColor: Color,
    val contentColor: Color
)

private fun chatIconOptions(): List<ChatIconOption> = listOf(
    ChatIconOption(CHAT_ICON_LIFE, R.string.chat_icon_life, Icons.Filled.Home, Color(0xFFFFD7C6), Color(0xFF8A2E20)),
    ChatIconOption(CHAT_ICON_WORK, R.string.chat_icon_work, Icons.Filled.Work, Color(0xFFD9ECFF), Color(0xFF1D5FA8)),
    ChatIconOption(CHAT_ICON_STUDY, R.string.chat_icon_study, Icons.Filled.School, Color(0xFFE8DFFF), Color(0xFF5F46A5)),
    ChatIconOption(CHAT_ICON_FOOD, R.string.chat_icon_food, Icons.Filled.Restaurant, Color(0xFFE3F3D8), Color(0xFF42703A)),
    ChatIconOption(CHAT_ICON_PLAY, R.string.chat_icon_play, Icons.Filled.FlightTakeoff, Color(0xFFFFE2B6), Color(0xFFA05A00))
)

@Composable
private fun ChatIconCircleButton(
    option: ChatIconOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(option.backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = stringResource(option.labelRes),
            modifier = Modifier.size(24.dp),
            tint = option.contentColor
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun UserMessageEditDialog(
    initialQuestion: MessageV2,
    attachments: List<ChatAttachmentDraft>,
    onFileSelected: (String) -> Unit,
    onCopyFailed: () -> Unit,
    onFileRemoved: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (MessageV2) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf(initialQuestion.content) }
    val questionFieldMaxLines = 8
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val filePath = withContext(Dispatchers.IO) {
                    copyFileToAppDirectory(context, it)
                }
                if (filePath != null) {
                    onFileSelected(filePath)
                } else {
                    onCopyFailed()
                }
            }
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.edit_question)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = question,
                    onValueChange = { question = it },
                    minLines = 3,
                    maxLines = questionFieldMaxLines,
                    label = { Text(stringResource(R.string.user_message)) }
                )
                AttachmentEditorSection(
                    attachments = attachments,
                    onAttachFileClick = { filePickerLauncher.launch("image/*") },
                    onFileRemoved = onFileRemoved
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasPendingOrFailedAttachments = attachments.any { it.status != ChatAttachmentDraft.Status.Ready }
            TextButton(
                enabled = !hasPendingOrFailedAttachments &&
                    (question.isNotBlank() || attachments.isNotEmpty()) &&
                    (question != initialQuestion.content || attachments.mapNotNull { it.attachment } != initialQuestion.attachments),
                onClick = { onConfirmRequest(initialQuestion.copy(content = question)) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AssistantMessageEditDialog(
    initialMessage: MessageV2,
    attachments: List<ChatAttachmentDraft>,
    onFileSelected: (String) -> Unit,
    onCopyFailed: () -> Unit,
    onFileRemoved: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (MessageV2, String) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var responseText by remember { mutableStateOf(initialMessage.effectiveContent()) }
    var thoughtsText by remember { mutableStateOf(initialMessage.effectiveThoughts()) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val filePath = withContext(Dispatchers.IO) {
                    copyFileToAppDirectory(context, it)
                }
                if (filePath != null) {
                    onFileSelected(filePath)
                } else {
                    onCopyFailed()
                }
            }
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.edit_assistant_message)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = responseText,
                    onValueChange = { responseText = it },
                    minLines = 3,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.assistant_message)) }
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    value = thoughtsText,
                    onValueChange = { thoughtsText = it },
                    minLines = 2,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.assistant_thoughts)) }
                )
                AttachmentEditorSection(
                    attachments = attachments,
                    onAttachFileClick = { filePickerLauncher.launch("image/*") },
                    onFileRemoved = onFileRemoved
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasPendingOrFailedAttachments = attachments.any { it.status != ChatAttachmentDraft.Status.Ready }
            TextButton(
                enabled = !hasPendingOrFailedAttachments &&
                    (responseText.isNotBlank() || thoughtsText.isNotBlank() || attachments.isNotEmpty()) &&
                    (
                        responseText != initialMessage.effectiveContent() ||
                            thoughtsText != initialMessage.effectiveThoughts() ||
                            attachments.mapNotNull { it.attachment } != initialMessage.attachments
                        ),
                onClick = {
                    onConfirmRequest(
                        initialMessage.copy(content = responseText),
                        thoughtsText
                    )
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AttachmentEditorSection(
    attachments: List<ChatAttachmentDraft>,
    onAttachFileClick: () -> Unit,
    onFileRemoved: (String) -> Unit
) {
    if (attachments.isNotEmpty()) {
        FileThumbnailRow(
            selectedAttachments = attachments,
            onFileRemoved = onFileRemoved
        )
    }
    TextButton(
        modifier = Modifier.padding(horizontal = 12.dp),
        onClick = onAttachFileClick
    ) {
        Text(text = stringResource(R.string.attach_file))
    }
}
