package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.LocalDynamicTheme
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeViewModel
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.util.TokenUsageStats
import dev.chungjungsoo.gptmobile.util.getClientTypeDisplayName
import dev.chungjungsoo.gptmobile.util.getDynamicThemeTitle
import dev.chungjungsoo.gptmobile.util.getThemeModeTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToPlatformSetting: (String) -> Unit,
    onNavigateToAboutPage: () -> Unit
) {
    val scrollState = rememberScrollState()
    val platformState by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val autoContextCompression by settingViewModel.autoContextCompression.collectAsStateWithLifecycle()
    val tokenUsageStats by settingViewModel.tokenUsageStats.collectAsStateWithLifecycle()
    val chatGroups by settingViewModel.chatGroups.collectAsStateWithLifecycle()
    val webDavConfig by settingViewModel.webDavConfig.collectAsStateWithLifecycle()
    val operationNotice by settingViewModel.operationNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { settingViewModel.importLocalBackup(context, it) }
    }

    LaunchedEffect(operationNotice) {
        operationNotice?.let { notice ->
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            settingViewModel.consumeOperationNotice()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingViewModel.fetchPlatforms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SettingTopBar(
                navigationOnClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            SettingsSectionHeader(text = stringResource(R.string.settings_general_section))
            ThemeSetting { settingViewModel.openThemeDialog() }

            AutoContextCompressionSetting(
                enabled = autoContextCompression,
                onCheckedChange = settingViewModel::updateAutoContextCompression
            )

            SettingItem(
                title = stringResource(R.string.token_usage),
                description = stringResource(R.string.token_usage_description),
                onItemClick = settingViewModel::openTokenStatsDialog,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = stringResource(R.string.chat_groups),
                description = stringResource(R.string.chat_groups_description, chatGroups.size),
                onItemClick = settingViewModel::openChatGroupDialog,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = stringResource(R.string.backup_restore_settings),
                description = stringResource(R.string.backup_restore_description),
                onItemClick = settingViewModel::openBackupDialog,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingsDivider()
            SettingsSectionHeader(text = stringResource(R.string.settings_providers_section))

            // Add Platform button
            SettingItem(
                title = stringResource(R.string.add_platform),
                description = stringResource(R.string.add_platform_description),
                onItemClick = onNavigateToAddPlatform,
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            // Dynamic platform list
            platformState.forEach { platform ->
                PlatformItem(
                    platform = platform,
                    onItemClick = { onNavigateToPlatformSetting(platform.uid) },
                    onDeleteClick = { settingViewModel.openDeleteDialog(platform.id) }
                )
            }

            SettingsDivider()
            SettingsSectionHeader(text = stringResource(R.string.settings_about_section))
            AboutPageItem(onItemClick = onNavigateToAboutPage)

            if (dialogState.isThemeDialogOpen) {
                ThemeSettingDialog(settingViewModel)
            }

            if (dialogState.isDeleteDialogOpen) {
                DeletePlatformDialog(settingViewModel)
            }

            if (dialogState.isTokenStatsDialogOpen) {
                TokenStatsDialog(
                    tokenUsageStats = tokenUsageStats,
                    onDismissRequest = settingViewModel::closeTokenStatsDialog
                )
            }

            if (dialogState.isChatGroupDialogOpen) {
                ChatGroupDialog(
                    groups = chatGroups,
                    onDismissRequest = settingViewModel::closeChatGroupDialog,
                    onConfirm = settingViewModel::updateChatGroups
                )
            }

            if (dialogState.isBackupDialogOpen) {
                BackupRecoveryDialog(
                    configUsername = webDavConfig.username,
                    configUrl = webDavConfig.url,
                    configPassword = webDavConfig.password,
                    readOnly = webDavConfig.readOnly,
                    onDismissRequest = settingViewModel::closeBackupDialog,
                    onSaveWebDav = { username, url, password ->
                        settingViewModel.updateWebDavConfig(username, url, password)
                        settingViewModel.closeBackupDialog()
                    },
                    onPull = settingViewModel::downloadWebDavConfig,
                    onUpload = settingViewModel::uploadWebDavConfig,
                    onClear = settingViewModel::clearWebDavConfig,
                    onExport = {
                        settingViewModel.closeBackupDialog()
                        settingViewModel.exportLocalBackup(context)
                    },
                    onImport = {
                        settingViewModel.closeBackupDialog()
                        backupImportLauncher.launch("application/json")
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingTopBar(
    navigationOnClick: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.settings),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = navigationOnClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        }
    )
}

@Composable
fun ThemeSetting(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.theme_settings),
        description = stringResource(R.string.theme_description),
        onItemClick = onItemClick,
        showTrailingIcon = false,
        showLeadingIcon = false
    )
}

@Composable
fun AutoContextCompressionSetting(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.auto_context_compression)) },
        supportingContent = { Text(stringResource(R.string.auto_context_compression_description)) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
fun TokenStatsDialog(
    tokenUsageStats: TokenUsageStats?,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        title = { Text(stringResource(R.string.token_usage)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (tokenUsageStats == null) {
                    Text(stringResource(R.string.loading))
                } else {
                    Text(stringResource(R.string.total_chats_tokens, tokenUsageStats.totalChats, tokenUsageStats.totalEstimatedTokens))
                    Text(stringResource(R.string.total_messages_tokens, tokenUsageStats.totalMessages))
                    Text(stringResource(R.string.compacted_tokens_saved, tokenUsageStats.totalCompactedTokensSaved))
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            SettingTextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
fun ChatGroupDialog(
    groups: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val defaultGroup = stringResource(R.string.default_chat_group)
    var editingGroups by remember(groups) {
        mutableStateOf(groups.ifEmpty { listOf(defaultGroup) })
    }
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.chat_groups)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                editingGroups.forEachIndexed { index, group ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = group,
                            onValueChange = { value ->
                                editingGroups = editingGroups.toMutableList().also { list ->
                                    list[index] = value.take(16)
                                }
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.chat_group_index, index + 1)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = editingGroups.size > 1,
                            onClick = {
                                editingGroups = editingGroups.toMutableList().also { list ->
                                    list.removeAt(index)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
                SettingTextButton(
                    enabled = editingGroups.size < 5,
                    onClick = { editingGroups = editingGroups + "" }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.add_chat_group))
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            SettingTextButton(
                enabled = editingGroups.any { it.isNotBlank() },
                onClick = { onConfirm(editingGroups) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            SettingTextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun BackupRecoveryDialog(
    configUsername: String,
    configUrl: String,
    configPassword: String,
    readOnly: Boolean,
    onDismissRequest: () -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onPull: (String, String, String) -> Unit,
    onUpload: (String, String, String) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var username by remember(configUsername) { mutableStateOf(configUsername) }
    var url by remember(configUrl) { mutableStateOf(configUrl) }
    var password by remember(configPassword) { mutableStateOf(configPassword) }
    var showInfo by remember { mutableStateOf(false) }
    val configComplete = username.isNotBlank() && url.isNotBlank() && password.isNotBlank()
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.backup_restore_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        onClick = onExport
                    ) {
                        Text(stringResource(R.string.export_local_backup))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        onClick = onImport
                    ) {
                        Text(stringResource(R.string.import_local_backup))
                    }
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.webdav_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_username)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_url)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.webdav_password)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackupIconButton(
                        icon = ImageVector.vectorResource(id = R.drawable.ic_backup_download),
                        contentDescription = stringResource(R.string.webdav_pull),
                        enabled = configComplete,
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = { onPull(username.trim(), url.trim(), password) }
                    )
                    BackupIconButton(
                        icon = ImageVector.vectorResource(id = R.drawable.ic_backup_upload),
                        contentDescription = stringResource(R.string.webdav_upload),
                        enabled = configComplete && !readOnly,
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = { onUpload(username.trim(), url.trim(), password) }
                    )
                    BackupIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.webdav_clear),
                        enabled = true,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onClear
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            SettingTextButton(
                enabled = configComplete,
                onClick = { onSaveWebDav(username.trim(), url.trim(), password) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingTextButton(onClick = { showInfo = true }) {
                    Text(stringResource(R.string.explanation))
                }
                SettingTextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (showInfo) {
        AlertDialog(
            title = { Text(stringResource(R.string.backup_restore_info_title)) },
            text = { Text(stringResource(R.string.backup_restore_info)) },
            onDismissRequest = { showInfo = false },
            confirmButton = {
                SettingTextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@Composable
private fun BackupIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    contentColor: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = Modifier.size(56.dp),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun AboutPageItem(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.about),
        description = stringResource(R.string.about_description),
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = false
    )
}

@Composable
fun ThemeSettingDialog(
    settingViewModel: SettingViewModelV2 = hiltViewModel()
) {
    val themeViewModel = LocalThemeViewModel.current
    AlertDialog(
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(R.string.dynamic_theme), style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                DynamicTheme.entries.forEach { theme ->
                    RadioItem(
                        title = getDynamicThemeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalDynamicTheme.current == theme
                    ) {
                        themeViewModel.updateDynamicTheme(theme)
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
                Text(text = stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                ThemeMode.entries.forEach { theme ->
                    RadioItem(
                        title = getThemeModeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalThemeMode.current == theme
                    ) {
                        themeViewModel.updateThemeMode(theme)
                    }
                }
            }
        },
        onDismissRequest = settingViewModel::closeThemeDialog,
        confirmButton = {
            SettingTextButton(
                onClick = settingViewModel::closeThemeDialog
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
fun PlatformItem(
    platform: PlatformV2,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    SettingItem(
        title = platform.name,
        description = "${getClientTypeDisplayName(platform.compatibleType)} - ${if (platform.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)}",
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = false
    )
}

@Composable
fun DeletePlatformDialog(
    settingViewModel: SettingViewModelV2 = hiltViewModel()
) {
    AlertDialog(
        title = {
            Text(stringResource(R.string.delete_platform))
        },
        text = {
            Text(stringResource(R.string.delete_platform_confirmation))
        },
        onDismissRequest = settingViewModel::closeDeleteDialog,
        confirmButton = {
            SettingTextButton(
                onClick = settingViewModel::confirmDelete
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            SettingTextButton(
                onClick = settingViewModel::closeDeleteDialog
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
