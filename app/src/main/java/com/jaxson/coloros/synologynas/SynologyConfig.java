package com.jaxson.coloros.synologynas;

import com.jaxson.coloros.synologynas.dsm.DsmUrlBuilder;

public final class SynologyConfig {
    // 保持历史配置和新建配置默认开启照片备份的既定行为
    public static final boolean DEFAULT_BACKUP_ENABLED = true;
    // 保持历史配置和新建配置使用同一默认备份目录
    public static final String DEFAULT_BACKUP_FOLDER = "ColorOS Backup";

    // 保存经过 HTTPS 规范化的 DSM 服务根地址
    private final String serverUrl;
    // 保存用于 DSM 会话认证的账户名称
    private final String username;
    // 保存仅在受控配置链路中传递的 DSM 密码
    private final String password;
    // 保存可为空的一次性验证码
    private final String otp;
    // 保存 ColorOS 相册浏览使用的 DSM 图片根目录
    private final String remoteRoot;
    // 保存连接验证后获取的 NAS 型号展示值
    private final String deviceModel;
    // 保存是否允许 ColorOS 相册执行照片备份
    private final boolean backupEnabled;
    // 保存远端图片根目录下的单层备份目录名称
    private final String backupFolder;

    /**
     * 创建允许显式设置备份行为但尚未识别设备型号的配置
     *
     * @param serverUrl DSM HTTPS 服务地址
     * @param username DSM 用户名
     * @param password DSM 密码
     * @param otp 可为空的一次性验证码
     * @param remoteRoot DSM 图片根目录
     * @param backupEnabled 照片备份开关
     * @param backupFolder 单层备份目录名称
     */
    public SynologyConfig(
            String serverUrl,
            String username,
            String password,
            String otp,
            String remoteRoot,
            boolean backupEnabled,
            String backupFolder
    ) {
        this(
                serverUrl,
                username,
                password,
                otp,
                remoteRoot,
                "",
                backupEnabled,
                backupFolder
        );
    }

    /**
     * 创建完整配置并集中执行地址、必填文本和备份目录校验
     *
     * @param serverUrl DSM HTTPS 服务地址
     * @param username DSM 用户名
     * @param password DSM 密码
     * @param otp 可为空的一次性验证码
     * @param remoteRoot DSM 图片根目录
     * @param deviceModel 可为空的 NAS 设备型号
     * @param backupEnabled 照片备份开关
     * @param backupFolder 单层备份目录名称
     */
    public SynologyConfig(
            String serverUrl,
            String username,
            String password,
            String otp,
            String remoteRoot,
            String deviceModel,
            boolean backupEnabled,
            String backupFolder
    ) {
        this.serverUrl = DsmUrlBuilder.normalizeBaseUrl(requireText(serverUrl, "DSM 地址"));
        this.username = requireText(username, "用户名");
        this.password = requireText(password, "密码");
        this.otp = otp == null ? "" : otp.trim();
        // 暂存规范化后的根目录，避免字段在路径校验前进入可观察状态
        String normalizedRoot = requireText(remoteRoot, "远端图片目录");
        if (!normalizedRoot.startsWith("/")) {
            throw new IllegalArgumentException("远端图片目录必须以 / 开头");
        }
        this.remoteRoot = normalizedRoot;
        this.deviceModel = deviceModel == null ? "" : deviceModel.trim();
        this.backupEnabled = backupEnabled;
        this.backupFolder = requireBackupFolder(backupFolder);
    }

    /**
     * 规范化并校验不可为空的配置文本
     *
     * @param value 待规范化文本
     * @param fieldName 用于明确失败字段的中文名称
     * @return 去除首尾空白后的非空文本
     */
    private static String requireText(String value, String fieldName) {
        // 承载统一去空白后的值，确保所有必填字段使用相同判定
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    /**
     * 校验备份目录是合法的单层目录名称
     *
     * @param value 用户配置的备份目录名称
     * @return 去除首尾空白后的合法目录名称
     */
    private static String requireBackupFolder(String value) {
        // 复用必填文本规则后继续执行单层路径约束
        String normalized = requireText(value, "备份文件夹");
        if (".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("备份文件夹名称无效");
        }
        // 遍历目录名称中的字符位置以拒绝路径分隔符和控制字符
        for (int index = 0; index < normalized.length(); index++) {
            // 表示当前接受业务路径约束检查的单个字符
            char item = normalized.charAt(index);
            if (item == '/' || item == '\\' || Character.isISOControl(item)) {
                throw new IllegalArgumentException("备份文件夹只能填写单个文件夹名称");
            }
        }
        return normalized;
    }

    /** @return 经过规范化的 DSM HTTPS 服务地址 */
    public String serverUrl() {
        return serverUrl;
    }

    /** @return DSM 会话认证用户名 */
    public String username() {
        return username;
    }

    /** @return DSM 会话认证密码 */
    public String password() {
        return password;
    }

    /** @return 可为空的一次性验证码 */
    public String otp() {
        return otp;
    }

    /** @return ColorOS 相册浏览使用的 DSM 图片根目录 */
    public String remoteRoot() {
        return remoteRoot;
    }

    /** @return 连接验证后识别的 NAS 设备型号 */
    public String deviceModel() {
        return deviceModel;
    }

    /** @return 是否允许 ColorOS 相册执行照片备份 */
    public boolean backupEnabled() {
        return backupEnabled;
    }

    /** @return DSM 图片根目录下的单层备份目录名称 */
    public String backupFolder() {
        return backupFolder;
    }

    /**
     * 在保留凭据、目录和备份设置的前提下更新设备型号
     *
     * @param model 连接验证后识别的 NAS 设备型号
     * @return 仅设备型号变化的新配置对象
     */
    public SynologyConfig withDeviceModel(String model) {
        return new SynologyConfig(
                serverUrl,
                username,
                password,
                otp,
                remoteRoot,
                requireText(model, "NAS 型号"),
                backupEnabled,
                backupFolder
        );
    }
}
