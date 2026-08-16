package com.jaxson.coloros.synologynas

import android.app.Application
import android.util.Log
import com.jaxson.coloros.synologynas.dsm.DsmClient
import com.jaxson.coloros.synologynas.security.CredentialStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.Executors

class SynologyApplication : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private const val TAG = "ColorOSSynologyNAS"
    }

    @Volatile
    private var xposedService: XposedService? = null
    private val metadataExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        try {
            val credentialStore = CredentialStore(this)
            credentialStore.load()?.let { config ->
                publishConfig(config)
                if (config.deviceModel().isBlank()) {
                    metadataExecutor.execute {
                        try {
                            val enriched = config.withDeviceModel(DsmClient(config).testConnection())
                            credentialStore.save(enriched)
                            publishConfig(enriched)
                            Log.i(TAG, "DSM model metadata refreshed: ${enriched.deviceModel()}")
                        } catch (error: Exception) {
                            Log.e(TAG, "DSM model metadata refresh failed", error)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Existing DSM configuration publication failed", error)
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService === service) {
            xposedService = null
        }
    }

    fun publishConfig(config: SynologyConfig) {
        val service = checkNotNull(xposedService) { "LSPosed 服务尚未连接" }
        check(service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L) {
            "当前 LSPosed 不支持远程配置"
        }
        RemoteConfigStore(service.getRemotePreferences(RemoteConfigStore.GROUP)).save(config)
        Log.i(TAG, "DSM configuration published to LSPosed")
    }
}
