package com.jaxson.coloros.synologynas.gallery;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface RemoteGalleryDataSource {
    boolean isConfigured();

    String configuredDeviceModel() throws IOException;

    String probeDeviceModel() throws IOException;

    List<RemoteAlbum> listAlbums(int offset, int limit) throws IOException;

    RemoteAlbum getAlbum(String albumId) throws IOException;

    List<RemotePhoto> listPhotos(String albumId, int offset, int limit) throws IOException;

    int photoCount() throws IOException;

    void downloadThumbnail(String photoId, String size, OutputStream output) throws IOException;

    void downloadOriginal(String photoId, OutputStream output) throws IOException;

    boolean deletePhotos(List<String> photoIds) throws IOException;
}
