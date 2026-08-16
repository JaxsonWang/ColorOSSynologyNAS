package com.jaxson.coloros.synologynas;

import android.content.SharedPreferences;

public final class RemoteConfigStore implements SynologyConfigSource {
    public static final String GROUP = "synology_dsm";

    private static final String KEY_CONFIG = "config_v1";

    private final SharedPreferences preferences;

    public RemoteConfigStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public void save(SynologyConfig config) {
        boolean committed = preferences.edit()
                .putString(KEY_CONFIG, RemoteConfigCodec.encode(config))
                .commit();
        if (!committed) {
            throw new IllegalStateException("群晖远程配置发布失败");
        }
    }

    @Override
    public boolean hasConfig() {
        String encoded = preferences.getString(KEY_CONFIG, null);
        return encoded != null && !encoded.isBlank();
    }

    @Override
    public SynologyConfig load() {
        String encoded = preferences.getString(KEY_CONFIG, null);
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        return RemoteConfigCodec.decode(encoded);
    }
}
