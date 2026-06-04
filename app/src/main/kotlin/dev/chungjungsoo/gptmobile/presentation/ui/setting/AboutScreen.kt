package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider.getUriForFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigationToLicense: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val clipboardManager = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val githubLink = stringResource(R.string.github_link)
    val scope = rememberCoroutineScope()
    val appTestMode by settingViewModel.appTestMode.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AboutTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationOnClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.version),
                description = "v$version",
                onItemClick = { scope.launch { clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("v$version", "v$version"))) } },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_info),
                        contentDescription = stringResource(R.string.version_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.license),
                description = stringResource(R.string.license_description),
                onItemClick = onNavigationToLicense,
                showTrailingIcon = true,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_license),
                        contentDescription = stringResource(R.string.license_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.github),
                onItemClick = { uriHandler.openUri(githubLink) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_github),
                        contentDescription = stringResource(R.string.github_icon)
                    )
                }
            )
            AppTestModeSetting(
                enabled = appTestMode,
                onCheckedChange = settingViewModel::updateAppTestMode
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.export_diagnostic_log),
                description = stringResource(R.string.export_diagnostic_log_description),
                onItemClick = {
                    scope.launch {
                        exportDiagnosticLog(context, settingViewModel)
                    }
                },
                showTrailingIcon = false,
                showLeadingIcon = false,
            )
        }
    }
}

@Composable
private fun AppTestModeSetting(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.app_test_mode)) },
        supportingContent = { Text(stringResource(R.string.app_test_mode_description)) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

private suspend fun exportDiagnosticLog(
    context: android.content.Context,
    settingViewModel: SettingViewModelV2
) {
    try {
        val appLog = withContext(Dispatchers.IO) { AppLogger.read(context) }
        val report = settingViewModel.createDiagnosticReport(appLog)
        val file = withContext(Dispatchers.IO) { AppLogger.writeDiagnosticFile(context, report) }
        val uri = getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_diagnostic_log)))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: context.getString(R.string.error), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.about),
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
