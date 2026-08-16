package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 负责 File Station Upload v2 的 multipart 头部生成与精确长度流复制 */
final class DsmUploadBody {
    /** File Station 上传合同固定使用 v2 */
    private static final int FILE_STATION_API_VERSION = 2;
    /** 单次读取缓冲区大小，在流式上传与内存占用之间保持原有平衡 */
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    /** 工具类不允许实例化 */
    private DsmUploadBody() {
    }

    /**
     * 生成文件内容之前的 multipart 字节，确保 file 部分最后出现
     *
     * @param boundary 当前上传请求的 multipart 边界
     * @param folder 远端目标目录
     * @param fileName 远端文件名
     * @return 文件内容之前的 UTF-8 multipart 字节
     */
    static byte[] prefix(String boundary, String folder, String fileName) {
        // body 按 DSM 官方字段顺序累积 multipart 头部
        StringBuilder body = new StringBuilder();
        appendPart(body, boundary, "api", "SYNO.FileStation.Upload");
        appendPart(body, boundary, "version", Integer.toString(FILE_STATION_API_VERSION));
        appendPart(body, boundary, "method", "upload");
        appendPart(body, boundary, "path", folder);
        appendPart(body, boundary, "create_parents", "true");
        appendPart(body, boundary, "overwrite", "false");
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(escapeHeaderValue(fileName))
                .append("\"\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 按 MediaStore 声明大小精确复制照片，并拒绝提前结束或多余字节
     *
     * @param input 本机照片输入流
     * @param output DSM multipart 输出流
     * @param expectedBytes MediaStore 声明的文件字节数
     * @throws IOException 本机读取或远端写入失败
     */
    static void copyExact(InputStream input, OutputStream output, long expectedBytes)
            throws IOException {
        // buffer 是流式读取照片的固定复用缓冲区
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        // remaining 记录仍必须从本机照片读取的字节数
        long remaining = expectedBytes;
        while (remaining > 0L) {
            // count 是本轮实际读取的照片字节数
            int count;
            try {
                count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            } catch (/* 本机照片读取失败 */ IOException error) {
                throw new DsmBackupReadException("读取本机照片失败", error);
            }
            if (count < 0) {
                throw new DsmBackupReadException(
                        "本机照片数据提前结束，缺少 " + remaining + " 字节"
                );
            }
            output.write(buffer, 0, count);
            remaining -= count;
        }
        // trailing 用于确认输入流没有超出 MediaStore 声明大小的额外字节
        int trailing;
        try {
            trailing = input.read();
        } catch (/* 校验末尾时的本机照片读取失败 */ IOException error) {
            throw new DsmBackupReadException("校验本机照片长度失败", error);
        }
        if (trailing >= 0) {
            throw new DsmBackupReadException("本机照片实际大小大于 MediaStore 记录");
        }
    }

    /**
     * 追加一个不含文件字节的普通 multipart 字段
     *
     * @param body 正在构造的 multipart 头部
     * @param boundary 当前上传边界
     * @param name 表单字段名
     * @param value 表单字段值
     */
    private static void appendPart(
            StringBuilder body,
            String boundary,
            String name,
            String value
    ) {
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"")
                .append(name)
                .append("\"\r\n\r\n")
                .append(value)
                .append("\r\n");
    }

    /**
     * 清理文件名中的 multipart 头部结构字符，保持现有替换语义
     *
     * @param value 原始远端文件名
     * @return 可安全写入 Content-Disposition 的文件名
     */
    private static String escapeHeaderValue(String value) {
        return value.replace("\\", "_").replace("\"", "_");
    }
}
