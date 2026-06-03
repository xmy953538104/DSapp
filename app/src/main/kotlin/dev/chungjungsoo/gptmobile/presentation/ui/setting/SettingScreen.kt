package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

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
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
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
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingTopBar(
                scrollBehavior = scrollBehavior,
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
                title = stringResource(R.string.backup_settings),
                description = stringResource(R.string.backup_settings_description),
                onItemClick = settingViewModel::openBackupDialog,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = stringResource(R.string.webdav_settings),
                description = stringResource(R.string.webdav_settings_description),
                onItemClick = settingViewModel::openWebDavDialog,
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
                BackupDialog(
                    onDismissRequest = settingViewModel::closeBackupDialog,
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

            if (dialogState.isWebDavDialogOpen) {
                WebDavConfigDialog(
                    configUsername = webDavConfig.username,
                    configUrl = webDavConfig.url,
                    configPassword = webDavConfig.password,
                    readOnly = webDavConfig.readOnly,
                    onDismissRequest = settingViewModel::closeWebDavDialog,
                    onConfirm = settingViewModel::updateWebDavConfig,
                    onPull = settingViewModel::downloadWebDavConfig,
                    onUpload = settingViewModel::uploadWebDavConfig,
                    onClear = settingViewModel::clearWebDavConfig
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
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
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
        },
        scrollBehavior = scrollBehavior
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
            TextButton(onClick = onDismissRequest) {
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
                TextButton(
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
            TextButton(
                enabled = editingGroups.any { it.isNotBlank() },
                onClick = { onConfirm(editingGroups) }
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
fun BackupDialog(
    onDismissRequest: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.backup_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogActionRow(
                    title = stringResource(R.string.export_local_backup),
                    description = stringResource(R.string.export_local_backup_description),
                    enabled = true,
                    onClick = onExport
                )
                DialogActionRow(
                    title = stringResource(R.string.import_local_backup),
                    description = stringResource(R.string.import_local_backup_description),
                    enabled = true,
                    onClick = onImport
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
fun WebDavConfigDialog(
    configUsername: String,
    configUrl: String,
    configPassword: String,
    readOnly: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onPull: (String, String, String) -> Unit,
    onUpload: (String, String, String) -> Unit,
    onClear: () -> Unit
) {
    var username by remember(configUsername) { mutableStateOf(configUsername) }
    var url by remember(configUrl) { mutableStateOf(configUrl) }
    var password by remember(configPassword) { mutableStateOf(configPassword) }
    val configComplete = username.isNotBlank() && url.isNotBlank() && password.isNotBlank()
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.webdav_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_username)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_url)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.webdav_password)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                DialogActionRow(
                    title = stringResource(R.string.webdav_pull),
                    description = stringResource(R.string.webdav_pull_description),
                    enabled = configComplete,
                    onClick = { onPull(username.trim(), url.trim(), password) }
                )
                DialogActionRow(
                    title = stringResource(R.string.webdav_upload),
                    description = stringResource(R.string.webdav_upload_description),
                    enabled = configComplete && !readOnly,
                    onClick = { onUpload(username.trim(), url.trim(), password) }
                )
                DialogActionRow(
                    title = stringResource(R.string.webdav_clear),
                    description = stringResource(R.string.webdav_clear_description),
                    enabled = true,
                    onClick = onClear
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = configComplete,
                onClick = { onConfirm(username.trim(), url.trim(), password) }
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
private fun DialogActionRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_round_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
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
            TextButton(
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
            TextButton(
                onClick = settingViewModel::confirmDelete
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = settingViewModel::closeDeleteDialog
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
