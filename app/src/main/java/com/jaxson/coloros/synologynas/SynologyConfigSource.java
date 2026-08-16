package com.jaxson.coloros.synologynas;

public interface SynologyConfigSource {
    boolean hasConfig();

    SynologyConfig load() throws Exception;
}
