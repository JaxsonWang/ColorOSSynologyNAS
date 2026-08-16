package com.jaxson.coloros.synologynas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jaxson.coloros.synologynas.dsm.DsmClient
import com.jaxson.coloros.synologynas.security.CredentialStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 承载 DSM 配置状态、连接验证以及凭据保存和发布边界 */
class MainActivity : ComponentActivity() {
    // 串行执行连接验证与配置保存，避免阻塞 Compose 主线程
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // 保存配置页正在编辑的 DSM 服务地址
    private var server by mutableStateOf("")

    // 保存配置页正在编辑的 DSM 用户名
    private var username by mutableStateOf("")

    // 保存配置页正在编辑且仅交给凭据存储的 DSM 密码
    private var password by mutableStateOf("")

    // 保存配置页正在编辑且允许为空的 DSM 一次性验证码
    private var otp by mutableStateOf("")

    // 保存 ColorOS 相册浏览使用的 DSM 图片根目录
    private var remoteRoot by mutableStateOf("/home/Photos")

    // 保存是否允许 ColorOS 相册执行照片备份
    private var backupEnabled by mutableStateOf(SynologyConfig.DEFAULT_BACKUP_ENABLED)

    // 保存远端图片根目录下的固定备份文件夹名称
    private var backupFolder by mutableStateOf(SynologyConfig.DEFAULT_BACKUP_FOLDER)

    // 保存当前需要向用户展示的连接或配置状态文案
    private var statusMessage by mutableStateOf<String?>(null)

    // 保存状态卡片当前使用的语义色调
    private var statusTone by mutableStateOf(StatusTone.NEUTRAL)

    // 标识连接验证和配置持久化是否正在后台执行
    private var busy by mutableStateOf(false)

    // 持有使用 Android Keystore 加密配置的唯一存储入口
    private lateinit var credentialStore: CredentialStore

    /**
     * 初始化凭据状态和 Compose 配置页
     *
     * @param savedInstanceState Android 保存的 Activity 状态；当前页面状态由持久配置恢复
     */
    override fun onCreate(
        // Android 保存的 Activity 状态，当前页面业务值仍由持久配置恢复
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        credentialStore = CredentialStore(this)
        loadSavedConfig()
        setContent {
            // 跟随系统明暗模式驱动 MIUIX 页面主题
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
                    onServerChange = {
                        // 表示配置页刚编辑完成的 DSM 服务地址
                        updatedServer -> server = updatedServer
                    },
                    onUsernameChange = {
                        // 表示配置页刚编辑完成的 DSM 用户名
                        updatedUsername -> username = updatedUsername
                    },
                    onPasswordChange = {
                        // 表示配置页刚编辑完成且只交给凭据链路的 DSM 密码
                        updatedPassword -> password = updatedPassword
                    },
                    onOtpChange = {
                        // 表示配置页刚编辑完成且允许为空的一次性验证码
                        updatedOtp -> otp = updatedOtp
                    },
                    onRemoteRootChange = {
                        // 表示配置页刚编辑完成的 DSM 图片根目录
                        updatedRemoteRoot -> remoteRoot = updatedRemoteRoot
                    },
                    onBackupEnabledChange = {
                        // 表示用户刚选择的照片备份开关状态
                        updatedBackupEnabled -> backupEnabled = updatedBackupEnabled
                    },
                    onBackupFolderChange = {
                        // 表示配置页刚编辑完成的固定备份文件夹名称
                        updatedBackupFolder -> backupFolder = updatedBackupFolder
                    },
                    onConnect = ::saveAndConnect,
                )
            }
        }
    }

    /** 结束页面时停止仍在排队或执行的连接任务 */
    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    /** 从加密凭据存储恢复已保存配置并更新页面状态 */
    private fun loadSavedConfig() {
        try {
            // 表示已解密且通过配置模型校验的持久化配置
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
        } catch (
            // 表示凭据解密或 Android Keystore 访问失败
            error: GeneralSecurityException,
        ) {
            showError(error)
        } catch (
            // 表示持久化配置缺失必要字段或处于非法状态
            error: IllegalStateException,
        ) {
            showError(error)
        }
    }

    /** 校验输入，在后台验证 DSM 连接，再保存并发布完整配置 */
    private fun saveAndConnect() {
        // 表示由当前表单值构造且已经通过本地规则校验的配置
        val config = try {
            readConfig()
        } catch (
            // 表示用户输入未通过地址、必填字段或目录规则校验
            error: IllegalArgumentException,
        ) {
            showError(error)
            return
        }

        busy = true
        statusMessage = getString(R.string.status_testing)
        statusTone = StatusTone.ACTIVE
        executor.execute {
            try {
                // 表示完成 DSM 登录、目录访问和型号读取后的可发布配置
                val connectedConfig = config.withDeviceModel(DsmClient(config).testConnection())
                (application as SynologyApplication).saveAndPublishConfig(connectedConfig)
                runOnUiThread {
                    busy = false
                    statusMessage = getString(R.string.status_connected)
                    statusTone = StatusTone.SUCCESS
                }
            } catch (
                // 表示 DSM 网络、协议或远端目录验证失败
                error: IOException,
            ) {
                showConnectionError(error)
            } catch (
                // 表示凭据加密或 Android Keystore 写入失败
                error: GeneralSecurityException,
            ) {
                showConnectionError(error)
            } catch (
                // 表示本地提交或 LSPosed 远程配置发布失败
                error: IllegalStateException,
            ) {
                showConnectionError(error)
            }
        }
    }

    /** @return 由当前表单值构造并完成统一校验的群晖配置 */
    private fun readConfig() = SynologyConfig(
        server,
        username,
        password,
        otp,
        remoteRoot,
        backupEnabled,
        backupFolder,
    )

    /**
     * 回到主线程结束忙碌状态并展示连接失败
     *
     * @param error 已由连接、凭据或发布边界明确抛出的失败
     */
    private fun showConnectionError(
        // 已由连接、凭据或发布边界明确抛出的失败
        error: Throwable,
    ) {
        runOnUiThread {
            busy = false
            showError(error)
        }
    }

    /**
     * 将明确失败转换成配置页可见的错误状态
     *
     * @param error 需要向用户展示的具体失败
     */
    private fun showError(
        // 需要转换为配置页可见状态的具体失败
        error: Throwable,
    ) {
        statusMessage = getString(R.string.status_error, error.message)
        statusTone = StatusTone.ERROR
    }
}
