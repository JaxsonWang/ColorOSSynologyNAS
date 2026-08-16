package com.jaxson.coloros.synologynas.backup;

import java.util.ArrayList;
import java.util.List;

public final class BackupUploadRequest {
    private final String targetDeviceUserId;
    private final String phoneDeviceId;
    private final String phoneDeviceName;
    private final String originalName;
    private final long fileSize;
    private final BackupInputSource inputSource;
    private final ColorOsFileHash fileHash;
    private final List<String> deviceAlbumNames;

    public BackupUploadRequest(
            String targetDeviceUserId,
            String phoneDeviceId,
            String phoneDeviceName,
            String originalName,
            long fileSize,
            BackupInputSource inputSource,
            String fileHash,
            List<String> deviceAlbumNames
    ) {
        this.targetDeviceUserId = requireText(targetDeviceUserId, "目标 NAS 设备 ID");
        this.phoneDeviceId = requireText(phoneDeviceId, "手机设备 ID");
        this.phoneDeviceName = phoneDeviceName == null ? "" : phoneDeviceName.trim();
        this.originalName = requireText(originalName, "照片文件名");
        if (fileSize < 0L) {
            throw new IllegalArgumentException("照片大小不能为负数");
        }
        if (inputSource == null) {
            throw new IllegalArgumentException("照片输入流不能为空");
        }
        this.fileSize = fileSize;
        this.inputSource = inputSource;
        this.fileHash = ColorOsFileHash.parse(fileHash);
        this.deviceAlbumNames = deviceAlbumNames == null
                ? List.of()
                : List.copyOf(new ArrayList<>(deviceAlbumNames));
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    public String targetDeviceUserId() {
        return targetDeviceUserId;
    }

    public String phoneDeviceId() {
        return phoneDeviceId;
    }

    public String phoneDeviceName() {
        return phoneDeviceName;
    }

    public String originalName() {
        return originalName;
    }

    public long fileSize() {
        return fileSize;
    }

    public BackupInputSource inputSource() {
        return inputSource;
    }

    public String fileHash() {
        return fileHash.value();
    }

    public String stableHashSuffix() {
        return fileHash.stableSuffix();
    }

    public List<String> deviceAlbumNames() {
        return deviceAlbumNames;
    }
}
