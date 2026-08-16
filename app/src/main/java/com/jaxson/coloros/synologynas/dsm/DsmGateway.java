package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface DsmGateway {
    DsmApiCatalog discoverApis() throws IOException;

    String login(DsmApiCatalog catalog) throws IOException;

    String getDeviceModel(DsmApiCatalog catalog, String sid) throws IOException;

    List<RemoteMedia> listImages(DsmApiCatalog catalog, String sid) throws IOException;

    void download(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            OutputStream output
    ) throws IOException;

    void downloadThumbnail(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            String size,
            OutputStream output
    ) throws IOException;

    void delete(
            DsmApiCatalog catalog,
            String sid,
            List<RemoteMedia> media
    ) throws IOException;
}
