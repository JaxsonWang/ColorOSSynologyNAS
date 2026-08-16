package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupRepository;
import com.jaxson.coloros.synologynas.backup.BackupUploadRequest;
import com.jaxson.coloros.synologynas.backup.BackupUploadResult;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class GalleryBackupClient implements GalleryBackupService {
    private final BackupRepository repository;

    public GalleryBackupClient(BackupRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isConfigured() {
        return repository.isConfigured();
    }

    @Override
    public boolean isEnabled() {
        return repository.isEnabled();
    }

    @Override
    public Set<String> findExistingHashes(Collection<String> hashes) throws IOException {
        return repository.findExistingHashes(hashes);
    }

    @Override
    public BackupUploadResult upload(Object colorOsRequest) {
        try {
            return repository.upload(parseRequest(colorOsRequest));
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                    message(error)
            );
        }
    }

    public static String targetDeviceId(Object colorOsRequest)
            throws ReflectiveOperationException {
        Object value = readField(colorOsRequest, "a", "f24401a");
        if (!(value instanceof String deviceId)) {
            throw new IllegalStateException("ColorOS 备份请求缺少目标设备 ID");
        }
        return deviceId;
    }

    private static BackupUploadRequest parseRequest(Object request)
            throws ReflectiveOperationException {
        String originalName = stringField(request, "d");
        if (originalName.isBlank()) {
            originalName = stringField(request, "g");
        }
        Object provider = readField(request, "f");
        return new BackupUploadRequest(
                targetDeviceId(request),
                stringField(request, "b"),
                stringField(request, "c"),
                originalName,
                longField(request, "e"),
                () -> openInputStream(provider),
                stringField(request, "h"),
                stringListField(request, "k")
        );
    }

    private static InputStream openInputStream(Object provider) throws IOException {
        if (provider == null) {
            throw new IOException("ColorOS 相册未提供照片输入流生成器");
        }
        Method invoke = null;
        for (Method method : provider.getClass().getMethods()) {
            if ("invoke".equals(method.getName()) && method.getParameterCount() == 0) {
                invoke = method;
                break;
            }
        }
        if (invoke == null) {
            for (Method method : provider.getClass().getDeclaredMethods()) {
                if ("invoke".equals(method.getName()) && method.getParameterCount() == 0) {
                    invoke = method;
                    break;
                }
            }
        }
        if (invoke == null) {
            throw new IOException("ColorOS 相册输入流生成器契约不匹配");
        }
        try {
            invoke.setAccessible(true);
            Object value = invoke.invoke(provider);
            if (!(value instanceof InputStream input)) {
                throw new IOException("ColorOS 相册输入流生成器未返回 InputStream");
            }
            return input;
        } catch (IllegalAccessException error) {
            throw new IOException("ColorOS 相册输入流生成器不可访问", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new IOException("ColorOS 相册打开本机照片失败", cause);
        }
    }

    private static String stringField(Object target, String... names)
            throws ReflectiveOperationException {
        Object value = readField(target, names);
        return value == null ? "" : String.valueOf(value);
    }

    private static long longField(Object target, String... names)
            throws ReflectiveOperationException {
        Object value = readField(target, names);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("ColorOS 备份请求字段不是数字");
        }
        return number.longValue();
    }

    private static List<String> stringListField(Object target, String... names)
            throws ReflectiveOperationException {
        Object value = readField(target, names);
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static Object readField(Object target, String... names)
            throws ReflectiveOperationException {
        if (target == null) {
            throw new IllegalArgumentException("ColorOS 备份请求为空");
        }
        for (String name : names) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        throw new NoSuchFieldException(
                "ColorOS 备份请求缺少字段: " + String.join("/", names)
        );
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
