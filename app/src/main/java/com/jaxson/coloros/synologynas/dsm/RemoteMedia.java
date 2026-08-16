package com.jaxson.coloros.synologynas.dsm;

/** DSM 远端图片的不可变元数据 */
public final class RemoteMedia {
    /** DSM 返回的完整远端路径，也是后续读取和删除的原始标识 */
    private final String remotePath;
    /** 远端文件名 */
    private final String name;
    /** 远端文件字节数；DSM 缺失时为 -1 */
    private final long size;
    /** 远端修改时间秒值；DSM 缺失时为 0 */
    private final long modifiedSeconds;
    /** 按扩展名推导的图片 MIME 类型 */
    private final String mimeType;

    /**
     * 创建一条不可变远端图片记录
     *
     * @param remotePath DSM 完整远端路径
     * @param name 远端文件名
     * @param size 文件字节数
     * @param modifiedSeconds 修改时间秒值
     * @param mimeType 图片 MIME 类型
     */
    public RemoteMedia(
            String remotePath,
            String name,
            long size,
            long modifiedSeconds,
            String mimeType
    ) {
        this.remotePath = remotePath;
        this.name = name;
        this.size = size;
        this.modifiedSeconds = modifiedSeconds;
        this.mimeType = mimeType;
    }

    /** @return DSM 完整远端路径 */
    public String remotePath() {
        return remotePath;
    }

    /** @return 远端文件名 */
    public String name() {
        return name;
    }

    /** @return 远端文件字节数 */
    public long size() {
        return size;
    }

    /** @return 远端修改时间秒值 */
    public long modifiedSeconds() {
        return modifiedSeconds;
    }

    /** @return 图片 MIME 类型 */
    public String mimeType() {
        return mimeType;
    }
}
