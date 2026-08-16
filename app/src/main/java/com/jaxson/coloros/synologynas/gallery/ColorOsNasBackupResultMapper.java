package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupUploadResult;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ColorOsNasBackupResultMapper {
    // 定位 ColorOS 备份 hash 查询结果的密封层次
    private static final String BACKUP_HASH_RESULT = "com.oplus.aiunit.vision.yjq";
    // 定位 ColorOS 备份上传结果的密封层次
    private static final String BACKUP_UPLOAD_RESULT = "com.oplus.aiunit.vision.teq";
    // 定位 ColorOS 备份目标路径 DTO
    private static final String BACKUP_PATH = "com.oplus.aiunit.vision.ycq";
    // 定位 ColorOS 备份失败结果使用的错误码枚举
    private static final String BACKUP_UPLOAD_ERROR =
            "com.oplus.gallery.framework.abilities.cloudsync.nas.api.model."
                    + "NasBackupUploadErrorCode";
    // 输出备份映射结果但不包含凭据、SID 或文件内容
    private static final Logger LOGGER = Logger.getLogger("ColorOSSynologyNAS");

    // 解析 ColorOS 私有备份 DTO 的相册类加载器
    private final ClassLoader galleryClassLoader;
    // 执行群晖备份查重与上传的服务
    private final GalleryBackupService backupService;

    // 固定当前相册版本的备份服务与私有 DTO 解析边界
    ColorOsNasBackupResultMapper(
            ClassLoader galleryClassLoader, // 解析 ColorOS 私有 DTO 的类加载器
            GalleryBackupService backupService // 执行群晖备份操作的服务
    ) {
        this.galleryClassLoader = galleryClassLoader;
        this.backupService = backupService;
    }

    // 查询已备份 hash，并映射为 ColorOS 的成功或失败结果 DTO
    Object hashResult(List<String> hashes /* 待确认的本地照片内容 hash */)
            throws ReflectiveOperationException {
        try {
            // DSM 备份索引中已经存在的 hash 集合
            Set<String> existing = backupService.findExistingHashes(hashes);
            LOGGER.info("DSM backup hash query completed: requested="
                    + hashes.size() + ", existing=" + existing.size());
            // ColorOS hash 查询成功结果的运行时类型
            Class<?> type = Class.forName(
                    BACKUP_HASH_RESULT + "$b",
                    false,
                    galleryClassLoader
            );
            // 成功结果只接收已存在 hash 集合的构造器
            Constructor<?> constructor = type.getDeclaredConstructor(Set.class);
            constructor.setAccessible(true);
            return constructor.newInstance(existing);
        } catch (ReflectiveOperationException error /* 私有 DTO 解析或构造异常 */) {
            throw error;
        } catch (IOException error /* 群晖备份 hash 查询异常 */) {
            LOGGER.log(Level.WARNING, "DSM backup hash query failed", error);
            // ColorOS hash 查询失败结果的运行时类型
            Class<?> type = Class.forName(
                    BACKUP_HASH_RESULT + "$a",
                    false,
                    galleryClassLoader
            );
            // 失败结果接收错误码和可观察消息的构造器
            Constructor<?> constructor = type.getDeclaredConstructor(int.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(-1, ColorOsNasReflection.errorMessage(error));
        }
    }

    // 执行群晖备份上传并映射为 ColorOS 的上传结果 DTO
    Object upload(Object colorOsRequest /* ColorOS 的 seq 备份请求 DTO */)
            throws ReflectiveOperationException {
        // 群晖备份领域层返回的真实上传结果
        BackupUploadResult result = backupService.upload(colorOsRequest);
        if (result.status() == BackupUploadResult.Status.SUCCESS) {
            LOGGER.info("DSM backup upload completed: bytes=" + result.bytesWritten());
            return uploadSuccess(result);
        }
        // 将重复文件和真实失败分别映射到 ColorOS 已有错误码
        String errorCode = result.status() == BackupUploadResult.Status.ALREADY_EXISTS
                ? "FILE_ALREADY_EXISTS"
                : result.errorCode().name();
        LOGGER.warning("DSM backup upload result: code=" + errorCode
                + ", message=" + result.message());
        return uploadFailure(errorCode, result.message());
    }

    // 按当前构造器合约构造 ColorOS 备份上传成功 DTO
    private Object uploadSuccess(BackupUploadResult result /* DSM 成功结果 */)
            throws ReflectiveOperationException {
        // ColorOS 备份目标路径 DTO 的运行时类型
        Class<?> pathType = Class.forName(BACKUP_PATH, false, galleryClassLoader);
        // 备份目标路径 DTO 的当前版本构造器
        Constructor<?> pathConstructor = pathType.getDeclaredConstructor(
                String.class,
                String.class,
                long.class,
                long.class,
                boolean.class
        );
        pathConstructor.setAccessible(true);
        // 表达已写入目录和访问状态的 ColorOS 备份路径对象
        Object backupPath = pathConstructor.newInstance(
                result.backupFolder(),
                result.backupFolder(),
                0L,
                0L,
                true
        );

        // ColorOS 备份成功结果的运行时类型
        Class<?> resultType = Class.forName(
                BACKUP_UPLOAD_RESULT + "$b",
                false,
                galleryClassLoader
        );
        // 备份成功结果的当前版本构造器
        Constructor<?> resultConstructor = resultType.getDeclaredConstructor(
                pathType,
                String.class,
                long.class,
                int.class,
                int.class,
                ArrayList.class
        );
        resultConstructor.setAccessible(true);
        return resultConstructor.newInstance(
                backupPath,
                result.savedPath(),
                result.bytesWritten(),
                1,
                0,
                new ArrayList<>()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    // 按当前构造器合约构造 ColorOS 备份上传失败 DTO
    private Object uploadFailure(
            String errorCode, // ColorOS 已有备份错误枚举常量名
            String message // 需要向相册调用方暴露的真实失败消息
    ) throws ReflectiveOperationException {
        // ColorOS 备份错误码枚举的运行时类型
        Class<?> errorType = Class.forName(
                BACKUP_UPLOAD_ERROR,
                false,
                galleryClassLoader
        );
        // 与领域结果对应的 ColorOS 备份错误枚举值
        Object code = Enum.valueOf((Class<? extends Enum>) errorType, errorCode);
        // ColorOS 备份失败结果的运行时类型
        Class<?> resultType = Class.forName(
                BACKUP_UPLOAD_RESULT + "$a",
                false,
                galleryClassLoader
        );
        // 备份失败结果的当前版本构造器
        Constructor<?> constructor = resultType.getDeclaredConstructor(
                errorType,
                String.class,
                Integer.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(code, message, null);
    }
}
