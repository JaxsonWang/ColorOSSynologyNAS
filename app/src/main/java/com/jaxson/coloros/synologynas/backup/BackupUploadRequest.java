package com.jaxson.coloros.synologynas.backup;

public final class BackupUploadRequest {
    // 保存 ColorOS 相册提供的照片原始文件名
    private final String originalName;
    // 保存 ColorOS 相册报告的照片字节数
    private final long fileSize;
    // 保存可重复打开照片数据的输入源，以支持校验和上传两次读取
    private final BackupInputSource inputSource;
    // 保存经过格式校验的 ColorOS SHA-256 原生哈希
    private final ColorOsFileHash fileHash;

    /**
     * 创建不可变的单张照片备份请求并校验核心 ColorOS 合约字段
     *
     * @param originalName 照片原始文件名
     * @param fileSize 非负照片字节数
     * @param inputSource 可重复打开照片数据的输入源
     * @param fileHash ColorOS 提供的 SHA-256 原生哈希
     */
    public BackupUploadRequest(
            String originalName,
            long fileSize,
            BackupInputSource inputSource,
            String fileHash
    ) {
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
    }

    /**
     * 规范化并校验备份请求中的必填文本
     *
     * @param value 待规范化文本
     * @param fieldName 用于明确失败字段的中文名称
     * @return 去除首尾空白后的非空文本
     */
    private static String requireText(String value, String fieldName) {
        // 保存统一去除首尾空白后的请求文本
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    /** @return ColorOS 相册提供的照片原始文件名 */
    public String originalName() {
        return originalName;
    }

    /** @return ColorOS 相册报告的照片字节数 */
    public long fileSize() {
        return fileSize;
    }

    /** @return 可为校验和上传分别打开照片流的输入源 */
    public BackupInputSource inputSource() {
        return inputSource;
    }

    /** @return 经过格式校验的 ColorOS SHA-256 原生哈希 */
    public String fileHash() {
        return fileHash.value();
    }

    /** @return 用于稳定解决同名冲突的完整原生哈希后缀 */
    public String stableHashSuffix() {
        return fileHash.stableSuffix();
    }
}
