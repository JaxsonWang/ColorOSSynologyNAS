package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupPath;
import com.jaxson.coloros.synologynas.backup.BackupUploadResult;
import com.oplus.aiunit.vision.dpk;
import com.oplus.aiunit.vision.jjq;
import com.oplus.aiunit.vision.seq;
import com.oplus.aiunit.vision.teq;
import com.oplus.aiunit.vision.yjq;
import com.oplus.gallery.framework.abilities.cloudsync.nas.api.model.NasBackupUploadErrorCode;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ColorOsNasProviderProxyTest {
    @Test
    // 验证 dpk.o 只读取 Hook 缓存统计而不触发 DSM 清单加载
    public void returnsCachedGalleryStatsWithoutLoadingRemoteInventory() throws Exception {
        // 记录任何意外的远端清单加载调用
        CountingDataSource dataSource = new CountingDataSource();
        // 使用计数数据源创建群晖远端客户端
        GalleryRemoteClient client = new GalleryRemoteClient(dataSource);
        // 仅满足 dpk 函数式接口的原飞牛 Provider
        dpk original = deviceUserId /* 原 Provider 收到的设备标识 */ -> null;
        // 使用固定 Hook 缓存照片数创建待测 Provider 代理
        dpk proxy = (dpk) ColorOsNasProviderProxy.create(
                client,
                new RecordingBackupService(),
                original,
                getClass().getClassLoader(),
                () /* 返回 Hook 已缓存的照片数量 */ -> 1594
        );

        // dpk.o 映射出的 ColorOS jjq 图库统计
        jjq stats = proxy.o(GalleryContract.DEVICE_ID);

        assertEquals(1594, stats.a);
        assertEquals(0, dataSource.inventoryInvocations);
    }

    @Test
    // 验证群晖 Provider 同步和协程备份能力及 hash 查询结果一致
    public void exposesSynologyBackupCapabilityAndExistingHashes() throws Exception {
        // 预置备份开启状态和已存在 hash 的记录服务
        RecordingBackupService backupService = new RecordingBackupService();
        backupService.existingHashes = Set.of(
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210"
        );
        // 使用记录备份服务创建群晖 Provider 代理
        dpk proxy = proxy(
                backupService,
                deviceUserId /* 原 Provider 收到的设备标识 */ -> null
        );

        assertEquals(1, proxy.m(GalleryContract.DEVICE_ID));
        assertEquals(1, proxy.d(GalleryContract.DEVICE_ID, null));
        // 同步 hash 查询映射出的 ColorOS yjq 结果
        yjq result = proxy.i(
                GalleryContract.DEVICE_ID,
                new ArrayList<>(backupService.existingHashes)
        );

        assertTrue(result instanceof yjq.b);
        assertEquals(backupService.existingHashes, ((yjq.b) result).a);
    }

    @Test
    // 验证关闭备份后同步和协程能力查询都返回零
    public void hidesSynologyBackupCapabilityWhenDisabled() throws Exception {
        // 用于切换备份开关并记录调用的测试服务
        RecordingBackupService backupService = new RecordingBackupService();
        backupService.enabled = false;
        // 使用关闭备份的记录服务创建 Provider 代理
        dpk proxy = proxy(
                backupService,
                deviceUserId /* 原 Provider 收到的设备标识 */ -> null
        );

        assertEquals(0, proxy.m(GalleryContract.DEVICE_ID));
        assertEquals(0, proxy.d(GalleryContract.DEVICE_ID, null));
    }

    @Test
    // 验证 DSM 成功结果完整映射为 ColorOS teq.b 通知成功合同
    public void mapsSuccessfulUploadToColorOsNoticeSuccess() throws Exception {
        // 预置实际保存路径与字节数的记录备份服务
        RecordingBackupService backupService = new RecordingBackupService();
        backupService.uploadResult = BackupUploadResult.success(
                new BackupPath(
                        "/home/Photos/ColorOS Backup",
                        "IMG_1.jpg"
                ),
                3L
        );
        // 使用成功结果服务创建 Provider 代理
        dpk proxy = proxy(
                backupService,
                deviceUserId /* 原 Provider 收到的设备标识 */ -> null
        );

        // 同步上传映射出的 ColorOS teq 结果
        teq result = proxy.r(request(GalleryContract.DEVICE_ID));

        assertTrue(result instanceof teq.b);
        // 已确认类型的 ColorOS 上传成功 DTO
        teq.b success = (teq.b) result;
        assertEquals(1, success.d);
        assertEquals(0, success.e);
        assertEquals(3L, success.c);
        assertEquals("/home/Photos/ColorOS Backup/IMG_1.jpg", success.b);
        assertTrue(success.a.e);
    }

    @Test
    // 验证重复与真实失败都映射为失败 DTO 且不伪造成功
    public void mapsDuplicateAndUploadFailureWithoutFakeSuccess() throws Exception {
        // 依次返回重复和上传失败结果的记录服务
        RecordingBackupService backupService = new RecordingBackupService();
        // 使用可切换结果的记录服务创建 Provider 代理
        dpk proxy = proxy(
                backupService,
                deviceUserId /* 原 Provider 收到的设备标识 */ -> null
        );

        backupService.uploadResult = BackupUploadResult.alreadyExists("already uploaded");
        // 重复文件领域结果映射出的 ColorOS 失败 DTO
        teq duplicate = proxy.r(request(GalleryContract.DEVICE_ID));
        assertEquals(NasBackupUploadErrorCode.FILE_ALREADY_EXISTS, ((teq.a) duplicate).a);

        backupService.uploadResult = BackupUploadResult.failed(
                BackupUploadResult.ErrorCode.UPLOAD_FAILED,
                "DSM 416"
        );
        // DSM 上传失败领域结果映射出的 ColorOS 失败 DTO
        teq failed = proxy.r(request(GalleryContract.DEVICE_ID));
        assertEquals(NasBackupUploadErrorCode.UPLOAD_FAILED, ((teq.a) failed).a);
        assertEquals("DSM 416", ((teq.a) failed).b);
    }

    @Test
    // 验证 seq.a 精确分流群晖请求并将其他设备请求转发原 Provider
    public void routesRequestObjectByTargetDeviceAndForwardsOtherNas() throws Exception {
        // 记录实际进入群晖备份服务的上传次数
        RecordingBackupService backupService = new RecordingBackupService();
        // 记录实际转发到原飞牛 Provider 的上传次数
        final int[] originalUploads = {0};
        // 对图库统计和同步上传提供可观察行为的原飞牛 Provider
        dpk original = new dpk() {
            @Override
            // 本场景不使用原 Provider 图库统计
            public jjq o(String deviceUserId /* 原 Provider 目标设备标识 */) {
                return null;
            }

            @Override
            // 记录非群晖备份请求被原样转发
            public teq r(seq request /* 原 Provider 收到的备份请求 */) {
                originalUploads[0]++;
                return new teq.a(NasBackupUploadErrorCode.UNKNOWN, "original", null);
            }
        };
        // 同时持有群晖服务和原飞牛 Provider 的待测代理
        dpk proxy = proxy(backupService, original);

        proxy.r(request(GalleryContract.DEVICE_ID));
        proxy.r(request("feiniu-device"));

        assertEquals(1, backupService.uploadInvocations);
        assertEquals(1, originalUploads[0]);
    }

    @Test
    // 验证非群晖图库统计请求完整转发原 Provider 而不读取 Hook 缓存
    public void forwardsOtherNasGalleryStatsToOriginalProvider() throws Exception {
        // 原 Provider 应直接返回且保持对象身份的图库统计 DTO
        jjq originalStats = new jjq(23, 4);
        // 对非群晖设备返回固定统计的原飞牛 Provider
        dpk original = deviceUserId /* 原 Provider 收到的设备标识 */ -> originalStats;
        // 同时持有群晖缓存统计和原飞牛 Provider 的待测代理
        dpk proxy = proxy(new RecordingBackupService(), original);

        assertTrue(originalStats == proxy.o("feiniu-device"));
    }

    // 使用统一客户端和缓存照片数创建测试 Provider 代理
    private dpk proxy(
            RecordingBackupService backupService, // 记录群晖备份调用的服务
            dpk original // 接收非群晖请求的原 Provider
    ) throws Exception {
        return (dpk) ColorOsNasProviderProxy.create(
                new GalleryRemoteClient(new CountingDataSource()),
                backupService,
                original,
                getClass().getClassLoader(),
                () /* 返回统一测试缓存照片数量 */ -> 1594
        );
    }

    // 创建只改变目标设备标识的完整 ColorOS 备份请求夹具
    private static seq request(String targetDeviceId /* 目标 NAS 设备标识 */) {
        return new seq(
                targetDeviceId,
                "phone-id",
                "PLK110",
                "IMG_1.jpg",
                3L,
                new Object() {
                    @SuppressWarnings("unused")
                    // 模拟 ColorOS Kotlin Function0 返回照片输入流
                    public java.io.InputStream invoke() {
                        return new java.io.ByteArrayInputStream(new byte[]{1, 2, 3});
                    }
                },
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210",
                "2026-08-16 10:00",
                List.of("Camera")
        );
    }

    private static final class RecordingBackupService implements GalleryBackupService {
        // 保存 hash 查询应返回的已存在集合
        private Set<String> existingHashes = Set.of();
        // 保存下一次上传应返回的领域结果
        private BackupUploadResult uploadResult = BackupUploadResult.alreadyExists("duplicate");
        // 记录群晖备份上传实际调用次数
        private int uploadInvocations;
        // 控制同步和协程备份能力查询结果
        private boolean enabled = true;

        @Override
        // 返回测试场景设置的备份开关
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        // 返回测试场景预置的已存在 hash 集合
        public Set<String> findExistingHashes(
                Collection<String> hashes // Provider 请求确认的照片 hash
        ) {
            return existingHashes;
        }

        @Override
        // 记录上传调用并返回测试场景预置的领域结果
        public BackupUploadResult upload(
                Object colorOsRequest // Provider 转交的 ColorOS seq 请求
        ) {
            uploadInvocations++;
            return uploadResult;
        }
    }

    private static final class CountingDataSource implements RemoteGalleryDataSource {
        // 记录任何会导致 DSM 清单访问的相册列表调用
        private int inventoryInvocations;

        @Override
        // 为 Provider 状态枚举测试返回已配置
        public boolean isConfigured() {
            return true;
        }

        @Override
        // 返回配置中保存的测试 NAS 型号
        public String configuredDeviceModel() {
            return "DS220+";
        }

        @Override
        // 返回实时探测得到的测试 NAS 型号
        public String probeDeviceModel() {
            return "DS220+";
        }

        @Override
        // 记录远端清单访问并返回空相册列表
        public List<RemoteAlbum> listAlbums(
                int offset, // 请求的相册起始偏移
                int limit // 请求的相册最大数量
        ) {
            inventoryInvocations++;
            return List.of();
        }

        @Override
        // 本场景禁止读取单个远端相册
        public RemoteAlbum getAlbum(String albumId /* 远端相册稳定标识 */)
                throws IOException {
            throw new IOException("Unexpected getAlbum");
        }

        @Override
        // 本场景不提供远端照片清单
        public List<RemotePhoto> listPhotos(
                String albumId, // 远端相册稳定标识
                int offset, // 请求的照片起始偏移
                int limit // 请求的照片最大数量
        ) {
            return List.of();
        }

        @Override
        // 本场景禁止进入缩略图读取路径
        public void downloadThumbnail(
                String photoId, // 远端照片稳定标识
                String size, // 请求的缩略图尺寸
                OutputStream output // 接收缩略图的输出流
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        // 本场景禁止进入原图读取路径
        public void downloadOriginal(
                String photoId, // 远端照片稳定标识
                OutputStream output // 接收原图的输出流
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        // 本场景禁止进入远端删除路径
        public boolean deletePhotos(List<String> photoIds /* 待删除的照片标识 */) {
            throw new UnsupportedOperationException();
        }
    }
}
