package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public interface BackupHashStore {
    Set<String> findExisting(SynologyConfig config, Collection<String> hashes) throws IOException;

    void recordUploaded(SynologyConfig config, String hash) throws IOException;
}
