package com.jaxson.coloros.synologynas;

import android.content.SharedPreferences;

public final class RemoteConfigStore implements SynologyConfigSource {
    // 指定宿主进程与相册进程共享配置时使用的 RemotePreferences 分组
    public static final String GROUP = "synology_dsm";

    // 标识当前版本的完整配置 JSON，确保跨进程读取只有一个数据源
    private static final String KEY_CONFIG = "config_v1";

    // 持有由 libxposed RemotePreferences 提供的跨进程配置存储
    private final SharedPreferences preferences;

    /**
     * 绑定跨进程配置存储
     *
     * @param preferences 由宿主发布并供相册进程读取的偏好存储
     */
    public RemoteConfigStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * 原子提交完整配置 JSON，使提交失败直接暴露给调用方
     *
     * @param config 已校验且需要发布到相册进程的群晖配置
     */
    public void save(SynologyConfig config) {
        // 记录同步提交结果，避免把未发布成功的配置误报为可用
        boolean committed = preferences.edit()
                .putString(KEY_CONFIG, RemoteConfigCodec.encode(config))
                .commit();
        if (!committed) {
            throw new IllegalStateException("群晖远程配置发布失败");
        }
    }

    /** 判断 RemotePreferences 中是否存在非空的完整配置 */
    @Override
    public boolean hasConfig() {
        // 读取唯一配置键，空文本不视为可用配置
        String encoded = preferences.getString(KEY_CONFIG, null);
        return encoded != null && !encoded.isBlank();
    }

    /**
     * 读取并解码跨进程配置
     *
     * @return 已解码配置；尚未发布配置时返回 null
     */
    @Override
    public SynologyConfig load() {
        // 读取唯一配置键，保持与 hasConfig 相同的存在性判断
        String encoded = preferences.getString(KEY_CONFIG, null);
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        return RemoteConfigCodec.decode(encoded);
    }
}
