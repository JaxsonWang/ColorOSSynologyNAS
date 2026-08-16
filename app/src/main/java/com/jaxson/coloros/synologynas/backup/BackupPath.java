package com.jaxson.coloros.synologynas.backup;

public final class BackupPath {
    // 保存 DSM 上传接口接收的目标文件夹绝对路径
    private final String folder;
    // 保存经过安全字符处理的目标文件名
    private final String fileName;

    /**
     * 创建一个不可变的 DSM 备份目标路径
     *
     * @param folder DSM 目标文件夹绝对路径
     * @param fileName DSM 目标文件名
     */
    public BackupPath(String folder, String fileName) {
        this.folder = folder;
        this.fileName = fileName;
    }

    /** @return DSM 目标文件夹绝对路径 */
    public String folder() {
        return folder;
    }

    /** @return DSM 目标文件名 */
    public String fileName() {
        return fileName;
    }

    /** @return 由文件夹和文件名组成的 DSM 完整目标路径 */
    public String remotePath() {
        return folder + "/" + fileName;
    }
}
