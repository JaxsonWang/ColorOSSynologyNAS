package com.jaxson.coloros.synologynas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 表示配置页状态卡片使用的有限语义色调 */
internal enum class StatusTone {
    // 表示尚未开始连接或没有额外状态
    NEUTRAL,

    // 表示已有配置或正在执行连接验证
    ACTIVE,

    // 表示 DSM 连接、保存和发布均已成功
    SUCCESS,

    // 表示输入、连接、凭据或发布过程明确失败
    ERROR,
}

/**
 * 组合状态卡片和 DSM 配置表单，保持状态由 Activity 单向传入
 *
 * @param server 当前 DSM 服务地址
 * @param username 当前 DSM 用户名
 * @param password 当前 DSM 密码
 * @param otp 当前可为空的一次性验证码
 * @param remoteRoot 当前 DSM 图片根目录
 * @param backupEnabled 当前照片备份开关
 * @param backupFolder 当前固定备份文件夹名称
 * @param statusMessage 当前状态文案；为空时显示未连接
 * @param statusTone 当前状态语义色调
 * @param busy 是否正在验证、保存和发布配置
 * @param onServerChange DSM 地址变化回调
 * @param onUsernameChange DSM 用户名变化回调
 * @param onPasswordChange DSM 密码变化回调
 * @param onOtpChange 一次性验证码变化回调
 * @param onRemoteRootChange 图片根目录变化回调
 * @param onBackupEnabledChange 照片备份开关变化回调
 * @param onBackupFolderChange 固定备份文件夹变化回调
 * @param onConnect 保存并连接操作回调
 */
@Composable
internal fun NasConfigurationScreen(
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
    ) {
        // 表示 Scaffold 为状态栏和顶部栏预留的页面内边距
        innerPadding ->
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

/**
 * 展示当前远程预览连接状态，不承担连接探测或状态修改
 *
 * @param message 需要展示的状态文案
 * @param tone 决定指示点颜色的状态语义
 */
@Composable
private fun StatusCard(
    message: String,
    tone: StatusTone,
) {
    // 将有限状态语义映射为唯一的视觉指示色
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

/**
 * 展示 DSM、目录和备份设置，并将所有编辑操作转交给 Activity 状态源
 *
 * @param server 当前 DSM 服务地址
 * @param username 当前 DSM 用户名
 * @param password 当前 DSM 密码
 * @param otp 当前可为空的一次性验证码
 * @param remoteRoot 当前 DSM 图片根目录
 * @param backupEnabled 当前照片备份开关
 * @param backupFolder 当前固定备份文件夹名称
 * @param busy 是否正在验证、保存和发布配置
 * @param onServerChange DSM 地址变化回调
 * @param onUsernameChange DSM 用户名变化回调
 * @param onPasswordChange DSM 密码变化回调
 * @param onOtpChange 一次性验证码变化回调
 * @param onRemoteRootChange 图片根目录变化回调
 * @param onBackupEnabledChange 照片备份开关变化回调
 * @param onBackupFolderChange 固定备份文件夹变化回调
 * @param onConnect 保存并连接操作回调
 */
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
    // 用于在备份文件夹输入完成时收起输入焦点
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
