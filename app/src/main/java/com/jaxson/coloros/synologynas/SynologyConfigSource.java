package com.jaxson.coloros.synologynas;

import java.security.GeneralSecurityException;

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
