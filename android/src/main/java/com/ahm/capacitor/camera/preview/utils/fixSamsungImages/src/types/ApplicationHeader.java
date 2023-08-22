package com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types;

public class ApplicationHeader {

    public Integer number;
    public Integer startAddress;
    public Integer endAddress;

    public ApplicationHeader(Integer number, Integer startAddress) {
        this.number = number;
        this.startAddress = startAddress;
    }

    public ApplicationHeader(Integer number, Integer startAddress, Integer endAddress) {
        this.number = number;
        this.startAddress = startAddress;
        this.endAddress = endAddress;
    }
}