package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.dsm.RemoteMedia;

public final class RemotePhoto {
    private final String id;
    private final int galleryId;
    private final RemoteMedia media;

    public RemotePhoto(String id, int galleryId, RemoteMedia media) {
        this.id = id;
        this.galleryId = galleryId;
        this.media = media;
    }

    public String id() {
        return id;
    }

    public int galleryId() {
        return galleryId;
    }

    public RemoteMedia media() {
        return media;
    }
}
