package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;

/** 表达 overwrite=false 上传遇到同名远端文件 */
public final class DsmRemoteFileAlreadyExistsException extends IOException {
    /**
     * 创建携带远端路径说明的文件冲突异常
     *
     * @param message 面向备份仓储的冲突说明
     */
    public DsmRemoteFileAlreadyExistsException(String message) {
        super(message);
    }
}
