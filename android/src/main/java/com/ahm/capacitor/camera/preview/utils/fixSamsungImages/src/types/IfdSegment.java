package com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types;

public class IfdSegment {
    public int ifdOffset = -1;
    public int entryCount = -1;
    /*
     * Total Length in bytes of exif data entries
     */
    public int entriesLength = -1;
    public int endOfEntries = -1;
    public int endOfIfd = -1;

    public IfdSegment(int ifdOffset, int entryCount, int entriesLength, int endOfEntries, int endOfIfd) {
        this.ifdOffset = ifdOffset;
        this.entryCount = entryCount;
        this.entriesLength = entriesLength;
        this.endOfEntries = endOfEntries;
        this.endOfIfd = endOfIfd;
    }
}
