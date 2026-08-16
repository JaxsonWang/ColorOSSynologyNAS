package com.jaxson.coloros.synologynas.backup;

/** 表达可精确映射到 ColorOS 私有上传合约的备份结果 */
public final class BackupUploadResult {
    /** 区分远端成功、内容已存在和可观察失败三类结果 */
    public enum Status {
        // 表示照片已经成功写入 DSM 且哈希索引已保存
        SUCCESS,
        // 表示本地索引或 DSM 内容校验确认照片已经存在
        ALREADY_EXISTS,
        // 表示读取、上传或结果索引保存阶段发生可观察失败
        FAILED
    }

    /** 保留 ColorOS 已有备份错误枚举能够表达的失败分类 */
    public enum ErrorCode {
        // 表示 ColorOS 照片数据读取失败
        READ_DATA_FAILED,
        // 表示照片已由本地索引或 DSM 内容确认存在
        FILE_ALREADY_EXISTS,
        // 表示 DSM 发现、认证或照片上传失败
        UPLOAD_FAILED,
        // 表示远端成功后本地哈希索引保存失败
        UPLOAD_NOTICE_FAILED
    }

    // 保存备份操作的总体状态
    private final Status status;
    // 保存映射给 ColorOS 私有合约的错误分类
    private final ErrorCode errorCode;
    // 保存可直接展示或记录的明确结果信息
    private final String message;
    // 保存成功上传时使用的 DSM 目标文件夹
    private final String backupFolder;
    // 保存成功上传时写入的 DSM 完整路径
    private final String savedPath;
    // 保存 DSM 上传接口确认写入的字节数
    private final long bytesWritten;

    /**
     * 集中创建不可变备份结果，确保各工厂方法使用一致字段布局
     *
     * @param status 备份总体状态
     * @param errorCode 失败或已存在时的 ColorOS 错误分类
     * @param message 明确的结果信息
     * @param backupFolder 成功上传的 DSM 目标文件夹
     * @param savedPath 成功上传的 DSM 完整路径
     * @param bytesWritten DSM 确认写入的字节数
     */
    private BackupUploadResult(
            Status status, // 备份操作的总体状态
            ErrorCode errorCode, // 失败或已存在时的 ColorOS 错误分类
            String message, // 可观察的结果信息
            String backupFolder, // 成功上传使用的 DSM 目标文件夹
            String savedPath, // 成功上传写入的 DSM 完整路径
            long bytesWritten // DSM 上传接口确认写入的字节数
    ) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.backupFolder = backupFolder;
        this.savedPath = savedPath;
        this.bytesWritten = bytesWritten;
    }

    /**
     * 创建已经完成 DSM 上传与哈希落库的成功结果
     *
     * @param path 实际写入的 DSM 目标路径
     * @param bytesWritten DSM 确认写入的字节数
     * @return 成功备份结果
     */
    public static BackupUploadResult success(BackupPath path, long bytesWritten) {
        return new BackupUploadResult(
                Status.SUCCESS,
                null,
                "",
                path.folder(),
                path.remotePath(),
                bytesWritten
        );
    }

    /**
     * 创建不需要重复上传的已存在结果
     *
     * @param message 照片被判定为已存在的原因
     * @return 已存在备份结果
     */
    public static BackupUploadResult alreadyExists(String message) {
        return new BackupUploadResult(
                Status.ALREADY_EXISTS,
                ErrorCode.FILE_ALREADY_EXISTS,
                message,
                "",
                "",
                0L
        );
    }

    /**
     * 创建保留明确错误分类和原因的失败结果
     *
     * @param code 可映射给 ColorOS 的错误分类
     * @param message 实际失败原因
     * @return 失败备份结果
     */
    public static BackupUploadResult failed(ErrorCode code, String message) {
        return new BackupUploadResult(
                Status.FAILED,
                code,
                message,
                "",
                "",
                0L
        );
    }

    /** @return 备份操作的总体状态 */
    public Status status() {
        return status;
    }

    /** @return 失败或已存在时的 ColorOS 错误分类 */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** @return 明确的备份结果信息 */
    public String message() {
        return message;
    }

    /** @return 成功上传时使用的 DSM 目标文件夹 */
    public String backupFolder() {
        return backupFolder;
    }

    /** @return 成功上传时写入的 DSM 完整路径 */
    public String savedPath() {
        return savedPath;
    }

    /** @return DSM 上传接口确认写入的字节数 */
    public long bytesWritten() {
        return bytesWritten;
    }
}
