package com.jaxson.coloros.synologynas.backup;

import java.io.IOException;
import java.io.InputStream;

/** 为照片校验和上传分别提供从起点读取的独立输入流 */
@FunctionalInterface
public interface BackupInputSource {
    /**
     * 为每次读取操作打开独立照片输入流，以支持 MD5 校验后再次上传
     *
     * @return 从照片起始位置读取的输入流
     * @throws IOException ColorOS 照片数据无法读取
     */
    InputStream open() throws IOException;
}
