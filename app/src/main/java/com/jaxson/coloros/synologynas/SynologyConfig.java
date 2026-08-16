package com.jaxson.coloros.synologynas;

import com.jaxson.coloros.synologynas.dsm.DsmUrlBuilder;

public final class SynologyConfig {
    public static final boolean DEFAULT_BACKUP_ENABLED = true;
    public static final String DEFAULT_BACKUP_FOLDER = "ColorOS Backup";

    private final String serverUrl;
    private final String username;
    private final String password;
    private final String otp;
    private final String remoteRoot;
    private final String deviceModel;
    private final boolean backupEnabled;
    private final String backupFolder;

    public SynologyConfig(
            String serverUrl,
            String username,
            String password,
            String otp,
            String remoteRoot
    ) {
        this(
                serverUrl,
                username,
                password,
                otp,
                remoteRoot,
                "",
                DEFAULT_BACKUP_ENABLED,
                DEFAULT_BACKUP_FOLDER
        );
    }

    public SynologyConfig(
            String serverUrl,
            String username,
            String password,
            String otp,
            String remoteRoot,
            String deviceModel
    ) {
        this(
                serverUrl,
                username,
                password,
                otp,
                remoteRoot,
                deviceModel,
                DEFAULT_BACKUP_ENABLED,
                DEFAULT_BACKUP_FOLDER
        );
    }

    public SynologyConfig(
            String serverUrl,
            String username,
            String password,
            String otp,
            String remoteRoot,
            boolean backupEnabled,
            String backupFolder
    ) {
        this(
                serverUrl,
                username,
                password,
                otp,
                remoteRoot,
                "",
                backupEnabled,
                backupFolder
        );
    }

    public SynologyConfig(
            String serverUrl,
            String username,
            String password,
            String otp,
            String remoteRoot,
            String deviceModel,
            boolean backupEnabled,
            String backupFolder
    ) {
        this.serverUrl = DsmUrlBuilder.normalizeBaseUrl(requireText(serverUrl, "DSM 地址"));
        this.username = requireText(username, "用户名");
        this.password = requireText(password, "密码");
        this.otp = otp == null ? "" : otp.trim();
        String normalizedRoot = requireText(remoteRoot, "远端图片目录");
        if (!normalizedRoot.startsWith("/")) {
            throw new IllegalArgumentException("远端图片目录必须以 / 开头");
        }
        this.remoteRoot = normalizedRoot;
        this.deviceModel = deviceModel == null ? "" : deviceModel.trim();
        this.backupEnabled = backupEnabled;
        this.backupFolder = requireBackupFolder(backupFolder);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private static String requireBackupFolder(String value) {
        String normalized = requireText(value, "备份文件夹");
        if (".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("备份文件夹名称无效");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char item = normalized.charAt(index);
            if (item == '/' || item == '\\' || Character.isISOControl(item)) {
                throw new IllegalArgumentException("备份文件夹只能填写单个文件夹名称");
            }
        }
        return normalized;
    }

    public String serverUrl() {
        return serverUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String otp() {
        return otp;
    }

    public String remoteRoot() {
        return remoteRoot;
    }

    public String deviceModel() {
        return deviceModel;
    }

    public boolean backupEnabled() {
        return backupEnabled;
    }

    public String backupFolder() {
        return backupFolder;
    }

    public SynologyConfig withDeviceModel(String model) {
        return new SynologyConfig(
                serverUrl,
                username,
                password,
                otp,
                remoteRoot,
                requireText(model, "NAS 型号"),
                backupEnabled,
                backupFolder
        );
    }
}
