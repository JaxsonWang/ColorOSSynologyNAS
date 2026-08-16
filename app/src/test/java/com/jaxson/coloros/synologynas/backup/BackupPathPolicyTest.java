package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 验证备份目录拼接、名称清理和同名冲突路径规则 */
public final class BackupPathPolicyTest {
    /** 验证设备和相册名称不会改变配置指定的唯一备份目录 */
    @Test
    public void mapsEveryColorOsRequestToConfiguredBackupFolder() {
        // 构造包含路径保留字符的第一张 ColorOS 照片请求
        BackupUploadRequest first = request("IMG:1.jpg");
        // 构造来自其他设备和相册的第二张 ColorOS 照片请求
        BackupUploadRequest second = request("IMG:2.jpg");

        // 生成第一张照片的首选 DSM 路径
        BackupPath firstPath = BackupPathPolicy.primary(config(), first);
        // 生成第二张照片的首选 DSM 路径
        BackupPath secondPath = BackupPathPolicy.primary(config(), second);

        assertEquals(
                "/home/Photos/手机备份/IMG_1.jpg",
                firstPath.remotePath()
        );
        assertEquals(firstPath.folder(), secondPath.folder());
        assertEquals("/home/Photos/手机备份/IMG_2.jpg", secondPath.remotePath());
    }

    /** 验证同名不同内容使用完整 ColorOS 原生哈希生成稳定冲突路径 */
    @Test
    public void addsStableNativeHashSuffixForDifferentContentWithSameName() {
        // 构造需要验证冲突路径的固定照片请求
        BackupUploadRequest request = request("IMG_1.jpg");

        // 生成带完整原生哈希后缀的冲突路径
        BackupPath path = BackupPathPolicy.collision(config(), request);

        assertEquals(
                "/home/Photos/手机备份/"
                        + "IMG_1_0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210.jpg",
                path.remotePath()
        );
    }

    /** 验证规范化后没有可用字符的照片名被明确拒绝 */
    @Test
    public void rejectsFileNameWithoutUsableCharacters() {
        // 捕获全由尾部点组成的文件名触发的严格路径错误
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BackupPathPolicy.primary(config(), request("..."))
        );

        assertEquals("照片文件名没有可用字符", error.getMessage());
    }

    /** 验证 DSM 根目录不会在备份目录前产生重复斜线 */
    @Test
    public void joinsBackupFolderDirectlyUnderDsmRoot() {
        // 生成以 DSM 根目录为图片范围的首选备份路径
        BackupPath path = BackupPathPolicy.primary(config("/"), request("IMG_1.jpg"));

        assertEquals("/手机备份/IMG_1.jpg", path.remotePath());
    }

    /** @return 使用自定义备份目录的固定群晖测试配置 */
    private static SynologyConfig config() {
        return config("/home/Photos");
    }

    /**
     * 创建远端图片根目录可变的固定群晖测试配置
     *
     * @param remoteRoot 参与备份路径拼接的 DSM 图片根目录
     * @return 使用自定义备份目录的固定群晖配置
     */
    private static SynologyConfig config(String remoteRoot) {
        return new SynologyConfig(
                "https://nas.example.test",
                "user",
                "pass",
                "",
                remoteRoot,
                true,
                "手机备份"
        );
    }

    /**
     * 创建覆盖文件名输入的固定备份请求
     *
     * @param fileName ColorOS 照片原始文件名
     * @return 使用固定内容和原生哈希的备份请求
     */
    private static BackupUploadRequest request(String fileName) {
        return new BackupUploadRequest(
                fileName,
                3L,
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210"
        );
    }
}
