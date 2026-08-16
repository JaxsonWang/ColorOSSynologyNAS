package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.backup.BackupPath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface DsmBackupGateway {
    DsmApiCatalog discoverApis() throws IOException;

    String login(DsmApiCatalog catalog) throws IOException;

    Optional<String> md5(
            DsmApiCatalog catalog,
            String sid,
            String remotePath
    ) throws IOException;

    long upload(
            DsmApiCatalog catalog,
            String sid,
            BackupPath path,
            long fileSize,
            InputStream input
    ) throws IOException;
}
