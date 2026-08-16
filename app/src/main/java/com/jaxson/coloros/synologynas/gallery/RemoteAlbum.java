package com.jaxson.coloros.synologynas.gallery;

public final class RemoteAlbum {
    private final String id;
    private final int galleryId;
    private final String name;
    private final int imageCount;
    private final String coverPhotoId;
    private final int coverGalleryId;
    private final long updateTimeMillis;

    public RemoteAlbum(
            String id,
            int galleryId,
            String name,
            int imageCount,
            String coverPhotoId,
            int coverGalleryId,
            long updateTimeMillis
    ) {
        this.id = id;
        this.galleryId = galleryId;
        this.name = name;
        this.imageCount = imageCount;
        this.coverPhotoId = coverPhotoId;
        this.coverGalleryId = coverGalleryId;
        this.updateTimeMillis = updateTimeMillis;
    }

    public String id() {
        return id;
    }

    public int galleryId() {
        return galleryId;
    }

    public String name() {
        return name;
    }

    public int imageCount() {
        return imageCount;
    }

    public String coverPhotoId() {
        return coverPhotoId;
    }

    public int coverGalleryId() {
        return coverGalleryId;
    }

    public long updateTimeMillis() {
        return updateTimeMillis;
    }
}
