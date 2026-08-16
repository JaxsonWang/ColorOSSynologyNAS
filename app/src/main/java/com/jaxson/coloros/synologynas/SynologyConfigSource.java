package com.jaxson.coloros.synologynas;

import java.security.GeneralSecurityException;

/** 统一模块私有凭据存储与相册远程配置的读取合同 */
public interface SynologyConfigSource {
    /** 判断当前配置源是否已经保存可用配置 */
    boolean hasConfig();

    /**
     * 从当前配置源读取群晖配置
     *
     * @return 已保存配置；未配置时返回 null
     * @throws GeneralSecurityException 凭据读取或解密失败
     */
    SynologyConfig load() throws GeneralSecurityException;
}
