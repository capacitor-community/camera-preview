package com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.utils;

import java.util.ArrayList;

import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types.ApplicationHeader;
import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types.ByteAlignment;
import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types.IfdSegment;


public class JpegUtils {

    public static final byte[] SOI = { (byte) 0xFF, (byte) 0xD8 };
    public static final byte[] EOI = { (byte) 0xFF, (byte) 0xD9 };
    public static final byte[] SOS = { (byte) 0xFF, (byte) 0xD9 };

    public static boolean isBrokenInTheS23Way(byte[] data) {
        int exifHeaderLength = ((0xFF & data[4]) << 8) + (0xFF & data[5]);

        return data[exifHeaderLength + 2] != (byte) 0xFF && data[exifHeaderLength + 3] != (byte) 0xD9;
    }

    public static boolean isJpeg(byte[] data) {
        return data[0] == (byte) 0xFF && data[1] == (byte) 0xD8;
    }

    public static boolean hasExif(byte[] data) {
        final byte[] exifHeader = { 0x45, 0x78, 0x69, 0x66, 0x00, 0x00 };

        return ByteUtils.findInByteArray(data, exifHeader, 6, 12) != -1;
    }

    public static ByteAlignment getByteAlignment(byte[] data) {
        byte[] byteAlign = { data[12], data[13] };
        if (byteAlign[0] == (byte) 0x49 && byteAlign[1] == (byte) 0x49) {
            return ByteAlignment.INTEL;
        } else if (byteAlign[0] == (byte) 0x4D && byteAlign[1] == (byte) 0x4D) {
            return ByteAlignment.MOTOROLA;
        }
        return ByteAlignment.UNKNOWN;
    }

    public static ArrayList<Integer> getSoiMarkers(byte[] data) {
        ArrayList<Integer> soiMarkers = ByteUtils.findAllInByteArray(data, SOI);
        return soiMarkers;
    }

    public static ArrayList<Integer> getEoiMarkers(byte[] data) {
        ArrayList<Integer> eoiMarkers = ByteUtils.findAllInByteArray(data, EOI);
        return eoiMarkers;
    }

    public static ArrayList<ApplicationHeader> getAppSegments(byte[] data) {
        final byte[] APP = { (byte) 0xFF, (byte) 0xE0 };

        ArrayList<ApplicationHeader> AppSections = new ArrayList<ApplicationHeader>();

        ByteUtils.findAllByMostSignificantDigits(data, APP).forEach((Integer appIndex) -> {
            int indexInt = (0XFF & (data[appIndex + 1]));

            // shift left to get rid of first digit then shift right to return to proper
            // last digit value
            int appSegmentId = (indexInt << 28) >> 28;

            AppSections.add(new ApplicationHeader(appSegmentId, appIndex));
        });

        return AppSections;
    }

    public static ArrayList<ApplicationHeader> matchSegmentWithEOIs(byte[] data,
            ArrayList<ApplicationHeader> appSegments) {
        int lastEOI = 0;

        for (int i = appSegments.size() - 1; i >= 0; i--) {
            ApplicationHeader appSegment = appSegments.get(i);

            int possibleEOI = ByteUtils.findInByteArray(data, EOI, appSegment.startAddress.intValue());
            if (lastEOI != possibleEOI) {
                lastEOI = possibleEOI;
                appSegment.endAddress = possibleEOI;
            }
        }

        return appSegments;
    }

    public static int getFirstIfdOffeset(byte[] data) {
        if (data[14] != (byte) 0x00 || data[15] != (byte) 0x2A) {
            throw new Error("Can't find TAG marker after byte alignment.");
        }

        int firstByte = (0xFF & data[16]) * 0x1000000;
        int secondByte = (0xFF & data[17]) * 0x10000;
        int thirdByte = (0xFF & data[18]) * 0x100;
        int forthByte = (0xFF & data[19]);

        return firstByte + secondByte + thirdByte + forthByte;
    }

    public static IfdSegment getIfdDetails(byte[] data, int readIfdOffset) {
        final int END_OF_EXIF_PREAMBLE = 12;
        final int IFD_DATA_ENTRY_SIZE = 12;

        int ifdOffset = END_OF_EXIF_PREAMBLE + readIfdOffset;

        int entriesInIfd = ((0xFF & data[ifdOffset]) << 8)
                + (0xFF & data[ifdOffset + 1]);

        int entriesLength = entriesInIfd * IFD_DATA_ENTRY_SIZE;

        // 2 = bytes consumed by entriesInIfd read
        int endOfEntries = ifdOffset + 2 + entriesLength;

        // 4 = IFD pointer length
        int endOfIfd = endOfEntries + 4;

        return new IfdSegment(ifdOffset, entriesInIfd, entriesLength, endOfEntries, endOfIfd);
    }

    public static void setAsLastIfd(byte[] data, IfdSegment ifd) {
        for (int i = 0; i < 4; i++) {
            data[ifd.endOfEntries + i] = (byte) 0x00;
        }
    }

}
