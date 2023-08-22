package com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src;

import java.util.ArrayList;
import java.util.Arrays;

import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types.ApplicationHeader;
import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types.ByteAlignment;
import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.types.IfdSegment;
import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.utils.ByteUtils;
import com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.utils.JpegUtils;

public class ImageProcessor {

    public static byte[] processImage(byte[] data) {
        if (!JpegUtils.isJpeg(data)) {
            throw new Error("The file provided is not a jpeg");
        }

        if (!JpegUtils.hasExif(data)) {
            throw new Error("The file provided does not have exif data");
        }

        if (JpegUtils.getByteAlignment(data) != ByteAlignment.MOTOROLA) {
            throw new Error(
                    "Your file uses non-motorola byte alignment. I'm not writing code to handle that, sorry");
        }

        if (JpegUtils.isBrokenInTheS23Way(data)) {
            System.out.print(
                    "Warning: The stated length for APP01 doesn't correspond to the end of the exif section");
            System.out.println(" - Attempting to resolve the problem");

            ArrayList<ApplicationHeader> appMarkers = JpegUtils.getAppSegments(data);

            int brokenOffset = ((0xFF & data[4]) << 8) + (0xFF & data[5]);
            int trueOffset = 0xFFFF + 1 + brokenOffset + 2;

            if (data[trueOffset] != (byte) 0xFF || data[trueOffset + 1] != (byte) 0xD9) {
                throw new Error(
                        "Couldn't find end of APP0 with offset: " + ByteUtils.toHexString(trueOffset));
            }

            int thumbnailStartOfImage = appMarkers.get(1).startAddress - 2;

            if (data[thumbnailStartOfImage] != (byte) 0xFF || data[thumbnailStartOfImage + 1] != (byte) 0xD8) {
                throw new Error(
                        "Couldn't find SOI for APP0: " + ByteUtils.toHexString(trueOffset));
            }
            byte[] dataWithoutApp0 = ByteUtils.deleteSection(data, thumbnailStartOfImage, trueOffset);

            IfdSegment ifd0 = JpegUtils.getIfdDetails(dataWithoutApp0, JpegUtils.getFirstIfdOffeset(dataWithoutApp0));

            int nextIfdOffset = ByteUtils
                    .sumByteSegment(Arrays.copyOfRange(dataWithoutApp0, ifd0.endOfEntries,
                            ifd0.endOfIfd));

            // zero out the offset pointer since we're deleting the ifd its referring to
            JpegUtils.setAsLastIfd(dataWithoutApp0, ifd0);

            IfdSegment ifd1 = JpegUtils.getIfdDetails(dataWithoutApp0, nextIfdOffset);

            byte[] finalData = ByteUtils.deleteSection(dataWithoutApp0, ifd1.ifdOffset,
                    ifd1.endOfIfd);

            /*
             * There seems to consistently be an extra 16 bytes that I can't explain between
             * the end of the last IFD and the actual EOF marker for the exif/thumbnail
             * data.
             */
            final int MYSTERY_BYTES = 16;
            if (finalData[ifd1.ifdOffset + MYSTERY_BYTES] == (byte) 0xFF
                    && finalData[ifd1.ifdOffset + 1 + MYSTERY_BYTES] == (byte) 0xD9) {
                // - 2 is because app length read doesn't consume bytes
                int finalApp1Length = ifd1.ifdOffset + MYSTERY_BYTES - 2;
                finalData[4] = (byte) (finalApp1Length >> 8);
                finalData[5] = (byte) (finalApp1Length - ((finalApp1Length >> 8) << 8));
            }

            return finalData;
        }
        return data;
    }

}
