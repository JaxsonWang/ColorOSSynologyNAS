package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;

public final class DsmBackupReadException extends IOException {
    public DsmBackupReadException(String message) {
        super(message);
    }

    public DsmBackupReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
