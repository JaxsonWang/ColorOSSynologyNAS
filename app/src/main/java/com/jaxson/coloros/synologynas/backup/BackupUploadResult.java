package com.jaxson.coloros.synologynas.backup;

public final class BackupUploadResult {
    public enum Status {
        SUCCESS,
        ALREADY_EXISTS,
        FAILED
    }

    public enum ErrorCode {
        READ_DATA_FAILED,
        FILE_ALREADY_EXISTS,
        UPLOAD_FAILED,
        UPLOAD_NOTICE_FAILED,
        UNKNOWN
    }

    private final Status status;
    private final ErrorCode errorCode;
    private final String message;
    private final String backupFolder;
    private final String savedPath;
    private final long bytesWritten;

    private BackupUploadResult(
            Status status,
            ErrorCode errorCode,
            String message,
            String backupFolder,
            String savedPath,
            long bytesWritten
    ) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.backupFolder = backupFolder;
        this.savedPath = savedPath;
        this.bytesWritten = bytesWritten;
    }

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

    public Status status() {
        return status;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String message() {
        return message;
    }

    public String backupFolder() {
        return backupFolder;
    }

    public String savedPath() {
        return savedPath;
    }

    public long bytesWritten() {
        return bytesWritten;
    }
}
