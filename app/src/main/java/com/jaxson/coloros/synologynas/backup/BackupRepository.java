package com.jaxson.coloros.synologynas.backup;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public interface BackupRepository {
    boolean isConfigured();

    boolean isEnabled();

    Set<String> findExistingHashes(Collection<String> hashes) throws IOException;

    BackupUploadResult upload(BackupUploadRequest request);
}
