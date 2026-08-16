package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;

/** 表达本机备份照片读取或长度合同失败 */
public final class DsmBackupReadException extends IOException {
    /**
     * 创建仅含明确读取错误消息的异常
     *
     * @param message 面向备份调用方的错误说明
     */
    public DsmBackupReadException(String message) {
        super(message);
    }

    /**
     * 创建保留底层失败原因的读取异常
     *
     * @param message 面向备份调用方的错误说明
     * @param cause 底层读取或长度计算失败
     */
    public DsmBackupReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
