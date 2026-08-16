package com.jaxson.coloros.synologynas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jaxson.coloros.synologynas.dsm.DsmClient
import com.jaxson.coloros.synologynas.security.CredentialStore
import java.security.GeneralSecurityException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DEVICE_USER_ID = "device_user_id"
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var server by mutableStateOf("")
    private var username by mutableStateOf("")
    private var password by mutableStateOf("")
    private var otp by mutableStateOf("")
    private var remoteRoot by mutableStateOf("/home/Photos")
    private var backupEnabled by mutableStateOf(SynologyConfig.DEFAULT_BACKUP_ENABLED)
    private var backupFolder by mutableStateOf(SynologyConfig.DEFAULT_BACKUP_FOLDER)
    private var statusMessage by mutableStateOf<String?>(null)
    private var statusTone by mutableStateOf(StatusTone.NEUTRAL)
    private var busy by mutableStateOf(false)
    private lateinit var credentialStore: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        credentialStore = CredentialStore(this)
        loadSavedConfig()
        setContent {
            val themeController = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = themeController) {
                NasConfigurationScreen(
                    server = server,
                    username = username,
                    password = password,
                    otp = otp,
                    remoteRoot = remoteRoot,
                    backupEnabled = backupEnabled,
                    backupFolder = backupFolder,
                    statusMessage = statusMessage,
                    statusTone = statusTone,
                    busy = busy,
                    onServerChange = { server = it },
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onOtpChange = { otp = it },
                    onRemoteRootChange = { remoteRoot = it },
                    onBackupEnabledChange = { backupEnabled = it },
                    onBackupFolderChange = { backupFolder = it },
                    onConnect = ::saveAndConnect,
                )
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun loadSavedConfig() {
        try {
            val config = credentialStore.load() ?: return
            server = config.serverUrl()
            username = config.username()
            password = config.password()
            otp = config.otp()
            remoteRoot = config.remoteRoot()
            backupEnabled = config.backupEnabled()
            backupFolder = config.backupFolder()
            statusMessage = getString(R.string.status_configured)
            statusTone = StatusTone.ACTIVE
        } catch (error: GeneralSecurityException) {
            showError(error)
        } catch (error: IllegalStateException) {
            showError(error)
        }
    }

    private fun saveAndConnect() {
        val config = try {
            readConfig()
        } catch (error: IllegalArgumentException) {
            showError(error)
            return
        }

        busy = true
        statusMessage = getString(R.string.status_testing)
        statusTone = StatusTone.ACTIVE
        executor.execute {
            try {
                val connectedConfig = config.withDeviceModel(DsmClient(config).testConnection())
                credentialStore.save(connectedConfig)
                (application as SynologyApplication).publishConfig(connectedConfig)
                runOnUiThread {
                    busy = false
                    statusMessage = getString(R.string.status_connected)
                    statusTone = StatusTone.SUCCESS
                }
            } catch (error: Exception) {
                runOnUiThread {
                    busy = false
                    showError(error)
                }
            }
        }
    }

    private fun readConfig() = SynologyConfig(
        server,
        username,
        password,
        otp,
        remoteRoot,
        backupEnabled,
        backupFolder,
    )

    private fun showError(error: Throwable) {
        val message = error.message ?: error.javaClass.simpleName
        statusMessage = getString(R.string.status_error, message)
        statusTone = StatusTone.ERROR
    }
}

private enum class StatusTone {
    NEUTRAL,
    ACTIVE,
    SUCCESS,
    ERROR,
}

@Composable
private fun NasConfigurationScreen(
    server: String,
    username: String,
    password: String,
    otp: String,
    remoteRoot: String,
    backupEnabled: Boolean,
    backupFolder: String,
    statusMessage: String?,
    statusTone: StatusTone,
    busy: Boolean,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onRemoteRootChange: (String) -> Unit,
    onBackupEnabledChange: (Boolean) -> Unit,
    onBackupFolderChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = stringResource(R.string.screen_title))
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                StatusCard(
                    message = statusMessage ?: stringResource(R.string.status_ready),
                    tone = statusTone,
                )
            }
            item {
                ConfigurationCard(
                    server = server,
                    username = username,
                    password = password,
                    otp = otp,
                    remoteRoot = remoteRoot,
                    backupEnabled = backupEnabled,
                    backupFolder = backupFolder,
                    busy = busy,
                    onServerChange = onServerChange,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onOtpChange = onOtpChange,
                    onRemoteRootChange = onRemoteRootChange,
                    onBackupEnabledChange = onBackupEnabledChange,
                    onBackupFolderChange = onBackupFolderChange,
                    onConnect = onConnect,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    message: String,
    tone: StatusTone,
) {
    val indicatorColor = when (tone) {
        StatusTone.NEUTRAL -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        StatusTone.ACTIVE -> MiuixTheme.colorScheme.primary
        StatusTone.SUCCESS -> Color(0xFF32A852)
        StatusTone.ERROR -> Color(0xFFD43B3B)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(24.dp),
    ) {
        Text(
            text = stringResource(R.string.remote_preview),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Spacer(Modifier.size(10.dp))
            Text(text = message, style = MiuixTheme.textStyles.main)
        }
    }
}

@Composable
private fun ConfigurationCard(
    server: String,
    username: String,
    password: String,
    otp: String,
    remoteRoot: String,
    backupEnabled: Boolean,
    backupFolder: String,
    busy: Boolean,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onRemoteRootChange: (String) -> Unit,
    onBackupEnabledChange: (Boolean) -> Unit,
    onBackupFolderChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(20.dp),
    ) {
        Text(
            text = stringResource(R.string.dsm_configuration),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(Modifier.height(18.dp))
        TextField(
            value = server,
            onValueChange = onServerChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.server_label),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.username_label),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.password_label),
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = otp,
            onValueChange = onOtpChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.otp_label),
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = remoteRoot,
            onValueChange = onRemoteRootChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.remote_root_label),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.backup_enabled_label),
                style = MiuixTheme.textStyles.main,
            )
            Switch(
                checked = backupEnabled,
                onCheckedChange = onBackupEnabledChange,
                enabled = !busy,
            )
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = backupFolder,
            onValueChange = onBackupFolderChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.backup_folder_label),
            singleLine = true,
            enabled = !busy && backupEnabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        Spacer(Modifier.height(22.dp))
        TextButton(
            text = stringResource(R.string.save_and_connect),
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            minHeight = 56.dp,
            cornerRadius = 18.dp,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
