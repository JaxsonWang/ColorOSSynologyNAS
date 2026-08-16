package com.jaxson.coloros.synologynas.backup;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface BackupInputSource {
    InputStream open() throws IOException;
}
