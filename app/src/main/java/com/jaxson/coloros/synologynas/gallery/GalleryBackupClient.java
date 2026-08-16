package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupRepository;
import com.jaxson.coloros.synologynas.backup.BackupUploadRequest;
import com.jaxson.coloros.synologynas.backup.BackupUploadResult;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

public final class GalleryBackupClient implements GalleryBackupService {
    // 执行群晖备份查重、路径选择与上传的领域仓储
    private final BackupRepository repository;

    // 将 ColorOS 相册备份请求适配到群晖备份领域仓储
    public GalleryBackupClient(BackupRepository repository /* 群晖备份仓储 */) {
        this.repository = repository;
    }

    @Override
    // 返回用户是否开启 ColorOS 原生 NAS 备份入口
    public boolean isEnabled() {
        return repository.isEnabled();
    }

    @Override
    // 查询指定内容 hash 中已经成功上传到当前备份目录的集合
    public Set<String> findExistingHashes(
            Collection<String> hashes // ColorOS 请求确认的照片内容 hash
    ) throws IOException {
        return repository.findExistingHashes(hashes);
    }

    @Override
    // 解析当前版本 seq DTO 并执行上传，解析失败时返回明确读取失败结果
    public BackupUploadResult upload(Object colorOsRequest /* ColorOS 的 seq 请求 DTO */) {
        try {
            return repository.upload(parseRequest(colorOsRequest));
        } catch (ReflectiveOperationException | IllegalArgumentException error
                 /* ColorOS 请求字段或类型不符合固定合约 */) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                    message(error)
            );
        }
    }

    // 从当前 ColorOS 运行时 seq.a 精确字段读取备份目标设备标识
    public static String targetDeviceId(Object colorOsRequest /* ColorOS 的 seq 请求 DTO */)
            throws ReflectiveOperationException {
        // 当前版本请求 DTO 中用于分流 Provider 的目标设备字段值
        Object value = readField(colorOsRequest, "a");
        if (!(value instanceof String deviceId /* 经过类型确认的设备标识 */)) {
            throw new IllegalStateException("ColorOS 备份请求缺少目标设备 ID");
        }
        return deviceId;
    }

    // 按 16.50.8 固定字段合约重建备份领域请求，不读取未约定字段
    private static BackupUploadRequest parseRequest(Object request /* ColorOS 的 seq 请求 DTO */)
            throws ReflectiveOperationException {
        // ColorOS 请求中提供照片输入流的 Kotlin Function0 实例
        Object provider = readField(request, "f");
        return new BackupUploadRequest(
                stringField(request, "d"),
                longField(request, "e"),
                () /* 按仓储需要延迟打开照片输入流 */ -> openInputStream(provider),
                stringField(request, "h")
        );
    }

    // 调用 ColorOS 输入流生成器并要求其返回真实照片 InputStream
    private static InputStream openInputStream(Object provider /* Kotlin Function0 实例 */)
            throws IOException {
        if (provider == null) {
            throw new IOException("ColorOS 相册未提供照片输入流生成器");
        }
        // 当前 ColorOS 合约固定公开且无参数的 invoke 方法
        Method invoke;
        try {
            invoke = provider.getClass().getMethod("invoke");
        } catch (NoSuchMethodException error /* 输入流生成器缺少公开 invoke 方法 */) {
            throw new IOException("ColorOS 相册输入流生成器契约不匹配", error);
        }
        try {
            invoke.setAccessible(true);
            // ColorOS 输入流生成器本次返回的照片数据对象
            Object value = invoke.invoke(provider);
            if (!(value instanceof InputStream input /* 已确认的照片输入流 */)) {
                throw new IOException("ColorOS 相册输入流生成器未返回 InputStream");
            }
            return input;
        } catch (IllegalAccessException error /* 输入流生成器 invoke 不可访问 */) {
            throw new IOException("ColorOS 相册输入流生成器不可访问", error);
        } catch (InvocationTargetException error /* 输入流生成器内部调用异常 */) {
            // 输入流生成器内部抛出的真实失败原因
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new IOException("ColorOS 相册打开本机照片失败", cause);
        }
    }

    // 读取请求字符串字段，空值按原有 DTO 语义映射为空字符串
    private static String stringField(
            Object target, // ColorOS 的 seq 请求 DTO
            String name // 当前版本确认的运行时字段名
    ) throws ReflectiveOperationException {
        // 目标字段反射读取到的原始值
        Object value = readField(target, name);
        return value == null ? "" : String.valueOf(value);
    }

    // 读取请求数字字段并保持其 long 语义
    private static long longField(
            Object target, // ColorOS 的 seq 请求 DTO
            String name // 当前版本确认的运行时字段名
    ) throws ReflectiveOperationException {
        // 目标字段反射读取到的原始值
        Object value = readField(target, name);
        if (!(value instanceof Number number /* 已确认的数字字段值 */)) {
            throw new IllegalStateException("ColorOS 备份请求字段不是数字");
        }
        return number.longValue();
    }

    // 从当前 seq 运行时类型读取一个精确命名的 ColorOS 请求字段
    private static Object readField(
            Object target, // ColorOS 的 seq 请求 DTO
            String name // 当前版本确认的运行时字段名
    ) throws ReflectiveOperationException {
        if (target == null) {
            throw new IllegalArgumentException("ColorOS 备份请求为空");
        }
        // 当前 seq 运行时类型中与合同名称精确匹配的字段
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    // 提取备份请求解析错误的可观察消息
    private static String message(Throwable error /* 待映射到失败结果的异常 */) {
        // 异常显式携带的原始消息
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
