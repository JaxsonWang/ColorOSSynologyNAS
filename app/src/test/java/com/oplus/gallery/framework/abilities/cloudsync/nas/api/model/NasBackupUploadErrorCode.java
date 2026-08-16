package com.oplus.gallery.framework.abilities.cloudsync.nas.api.model;

// 模拟 ColorOS 备份上传错误码枚举合同
public enum NasBackupUploadErrorCode {
    READ_DATA_FAILED, // 模拟 ColorOS 读取本地照片失败错误
    FILE_NOT_EXIST, // 模拟 ColorOS 本地照片不存在错误
    FILE_ALREADY_EXISTS, // 模拟 ColorOS 远端文件已存在错误
    BACKUP_PATH_FAILED, // 模拟 ColorOS 备份目录处理失败错误
    DUPLICATE_CHECK_FAILED, // 模拟 ColorOS 重复检查失败错误
    UPLOAD_FAILED, // 模拟 ColorOS 远端上传失败错误
    UPLOAD_NOTICE_FAILED, // 模拟 ColorOS 上传通知失败错误
    UNKNOWN // 模拟原飞牛 Provider 测试返回的未知错误
}
