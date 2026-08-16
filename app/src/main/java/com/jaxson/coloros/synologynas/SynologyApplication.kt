package com.jaxson.coloros.synologynas

import android.app.Application
import android.util.Log
import com.jaxson.coloros.synologynas.security.CredentialStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.security.GeneralSecurityException

/** 维护 libxposed 服务连接，并发布模块 App 已保存的群晖配置 */
class SynologyApplication : Application(), XposedServiceHelper.OnServiceListener {
    /** 集中保存应用级日志常量，避免配置发布标签分散 */
    companion object {
        // 统一标识模块配置发布日志
        private const val TAG = "ColorOSSynologyNAS"
    }

    @Volatile
    // 保存当前已绑定且支持远程配置发布的 LSPosed 服务
    private var xposedService: XposedService? = null

    /** 注册 libxposed 服务监听，以建立模块配置发布通道 */
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    /**
     * 绑定服务后在应用级顺序边界内发布现有配置，不发起 DSM 网络请求
     *
     * @param service 当前可用于 RemotePreferences 发布的 libxposed 服务
     */
    @Synchronized
    override fun onServiceBind(
        // 当前可用于 RemotePreferences 发布的 libxposed 服务
        service: XposedService,
    ) {
        xposedService = service
        try {
            CredentialStore(this).load()?.let(::publishRemoteConfig)
        } catch (
            // 表示现有凭据无法从 Android Keystore 解密
            error: GeneralSecurityException,
        ) {
            Log.e(TAG, "Existing DSM configuration publication failed", error)
        } catch (
            // 表示现有配置不完整或 RemotePreferences 发布失败
            error: IllegalStateException,
        ) {
            Log.e(TAG, "Existing DSM configuration publication failed", error)
        }
    }

    /**
     * 仅在死亡通知对应当前服务时清除发布通道
     *
     * @param service libxposed 通知已经失效的服务实例
     */
    @Synchronized
    override fun onServiceDied(
        // libxposed 通知已经失效的服务实例
        service: XposedService,
    ) {
        if (xposedService === service) {
            xposedService = null
        }
    }

    /**
     * 在应用级顺序边界内先提交已验证凭据，再发布同一配置快照
     *
     * @param config 需要保存并发布给 ColorOS 相册进程的完整群晖配置
     */
    @Synchronized
    fun saveAndPublishConfig(
        // 需要按固定顺序保存并发布的完整群晖配置
        config: SynologyConfig,
    ) {
        CredentialStore(this).save(config)
        publishRemoteConfig(config)
    }

    /**
     * 将已保存配置提交到当前绑定服务的唯一 RemotePreferences 配置键
     *
     * @param config 已由凭据存储确认提交的完整群晖配置
     */
    private fun publishRemoteConfig(
        // 已由凭据存储确认提交且不得替换的配置快照
        config: SynologyConfig,
    ) {
        // 固定使用当前已绑定服务，服务缺失时直接暴露发布失败
        val service = checkNotNull(xposedService) { "LSPosed 服务尚未连接" }
        try {
            check(service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L) {
                "当前 LSPosed 不支持远程配置"
            }
            RemoteConfigStore(service.getRemotePreferences(RemoteConfigStore.GROUP)).save(config)
        } catch (
            // 表示 libxposed Binder 服务在配置发布期间失效
            error: XposedService.ServiceException,
        ) {
            throw IllegalStateException("群晖远程配置发布失败", error)
        }
        Log.i(TAG, "DSM configuration published to LSPosed")
    }
}
