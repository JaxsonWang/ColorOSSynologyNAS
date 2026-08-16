package com.jaxson.coloros.synologynas

import android.app.Application
import android.util.Log
import com.jaxson.coloros.synologynas.security.CredentialStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.security.GeneralSecurityException

/** 维护 libxposed 服务连接，并发布模块 App 已保存的群晖配置 */
class SynologyApplication : Application(), XposedServiceHelper.OnServiceListener {
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
     * 绑定服务后立即发布现有配置，不在服务回调中发起 DSM 网络请求
     *
     * @param service 当前可用于 RemotePreferences 发布的 libxposed 服务
     */
    override fun onServiceBind(service: XposedService) {
        xposedService = service
        try {
            CredentialStore(this).load()?.let(::publishConfig)
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
    override fun onServiceDied(service: XposedService) {
        if (xposedService === service) {
            xposedService = null
        }
    }

    /**
     * 将已验证配置同步提交到模块专属 RemotePreferences
     *
     * @param config 需要发布给 ColorOS 相册进程的完整群晖配置
     */
    fun publishConfig(config: SynologyConfig) {
        // 固定使用当前已绑定服务，服务缺失时直接暴露发布失败
        val service = checkNotNull(xposedService) { "LSPosed 服务尚未连接" }
        check(service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L) {
            "当前 LSPosed 不支持远程配置"
        }
        RemoteConfigStore(service.getRemotePreferences(RemoteConfigStore.GROUP)).save(config)
        Log.i(TAG, "DSM configuration published to LSPosed")
    }
}
