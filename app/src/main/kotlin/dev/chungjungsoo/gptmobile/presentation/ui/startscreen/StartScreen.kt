package dev.chungjungsoo.gptmobile.presentation.ui.startscreen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton

@Composable
fun StartScreen(
    startViewModel: StartViewModel = hiltViewModel(),
    onStartClick: () -> Unit,
    onImportComplete: () -> Unit
) {
    val context = LocalContext.current
    val notice by startViewModel.notice.collectAsStateWithLifecycle()
    var logoTapCount by remember { mutableIntStateOf(0) }
    var showOwnerWebDavDialog by rememberSaveable { mutableStateOf(false) }
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { startViewModel.importLocalBackup(context, it, onImportComplete) }
    }

    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            startViewModel.consumeNotice()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            StartScreenLogo(
                modifier = Modifier.clickable {
                    logoTapCount += 1
                    if (logoTapCount >= 5) {
                        logoTapCount = 0
                        showOwnerWebDavDialog = true
                    }
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            WelcomeText()
            PrimaryLongButton(
                onClick = onStartClick,
                text = stringResource(R.string.get_started)
            )
            PrimaryLongButton(
                onClick = { backupImportLauncher.launch("application/json") },
                text = stringResource(R.string.import_backup_start)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showOwnerWebDavDialog) {
        OwnerWebDavDialog(
            onDismissRequest = { showOwnerWebDavDialog = false },
            onConfirm = { password ->
                startViewModel.importOwnerWebDav(password, onImportComplete)
                showOwnerWebDavDialog = false
            }
        )
    }
}

@Composable
private fun OwnerWebDavDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        title = { Text(stringResource(R.string.hidden_webdav_import)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.webdav_password)) }
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank(),
                onClick = { onConfirm(password) }
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

@Preview
@Composable
fun StartScreenLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_chat_ai),
        contentDescription = stringResource(R.string.gpt_mobile_introduction_logo),
        modifier = modifier
            .padding(top = 54.dp)
            .size(172.dp)
            .clip(RoundedCornerShape(34.dp)),
        contentScale = ContentScale.Fit
    )
}

@Preview
@Composable
fun WelcomeText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
