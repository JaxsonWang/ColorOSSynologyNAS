package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupUploadResult;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public interface GalleryBackupService {
    boolean isConfigured();

    boolean isEnabled();

    Set<String> findExistingHashes(Collection<String> hashes) throws IOException;

    BackupUploadResult upload(Object colorOsRequest);
}
