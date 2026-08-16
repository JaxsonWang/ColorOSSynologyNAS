package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

/** 集中执行固定备份目录、文件名清理和同名冲突路径规则 */
public final class BackupPathPolicy {
    /** 禁止实例化仅承载备份路径规则的工具类 */
    private BackupPathPolicy() {
    }

    /**
     * 生成照片原始文件名对应的首选备份路径
     *
     * @param config 提供远端根目录和备份目录的群晖配置
     * @param request 提供 ColorOS 原始照片名称的备份请求
     * @return 首选 DSM 目标路径
     */
    public static BackupPath primary(SynologyConfig config, BackupUploadRequest request) {
        return new BackupPath(folder(config), fileName(request.originalName()));
    }

    /**
     * 在首选文件名已被不同内容占用时生成稳定冲突路径
     *
     * @param config 提供远端根目录和备份目录的群晖配置
     * @param request 提供原始名称和稳定哈希后缀的备份请求
     * @return 带 ColorOS 原生哈希后缀的 DSM 目标路径
     */
    public static BackupPath collision(SynologyConfig config, BackupUploadRequest request) {
        // 保存经过安全字符处理的原始文件名
        String original = fileName(request.originalName());
        // 标记扩展名前最后一个点的位置，首字符点不视为扩展名分隔符
        int dot = original.lastIndexOf('.');
        // 保存追加稳定哈希后缀前的文件主名
        String base = dot <= 0 ? original : original.substring(0, dot);
        // 保存需要原样附加到冲突文件名末尾的扩展名
        String extension = dot <= 0 ? "" : original.substring(dot);
        return new BackupPath(
                folder(config),
                base + "_" + request.stableHashSuffix() + extension
        );
    }

    /**
     * 生成配置根目录下唯一的照片备份文件夹路径
     *
     * @param config 提供远端根目录和单层备份目录的群晖配置
     * @return 不含末尾斜线的 DSM 备份文件夹路径
     */
    private static String folder(SynologyConfig config) {
        // 使用配置模型已经规范化的根目录直接拼接单层备份目录
        String root = config.remoteRoot();
        return "/".equals(root)
                ? root + config.backupFolder()
                : root + "/" + config.backupFolder();
    }

    /**
     * 把 ColorOS 提供的名称规范化为单个安全路径段
     *
     * @param value 待规范化的原始名称
     * @return 不包含路径分隔符和控制字符的单个路径段
     */
    static String safeSegment(String value) {
        // 保存逐步替换非法字符和尾部空白后的路径段
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "_");
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("[. ]+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("照片文件名没有可用字符");
        }
        return normalized;
    }

    /**
     * 按照片文件名规则严格规范化 ColorOS 原始名称
     *
     * @param originalName ColorOS 提供的照片原始名称
     * @return 可安全传入 DSM 上传路径的文件名
     */
    private static String fileName(String originalName) {
        return safeSegment(originalName);
    }
}
