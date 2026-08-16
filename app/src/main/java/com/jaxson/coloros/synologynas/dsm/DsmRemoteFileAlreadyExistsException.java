package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;

public final class DsmRemoteFileAlreadyExistsException extends IOException {
    public DsmRemoteFileAlreadyExistsException(String message) {
        super(message);
    }
}
