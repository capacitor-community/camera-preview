package com.ahm.capacitor.camera.preview.utils.fixSamsungImages.src.utils;

import java.util.ArrayList;

public class ByteUtils {

    public static String toHexString(Integer number) {
        return String.format("0x%08X", number);
    }

    public static String toHexString(byte number) {
        return String.format("0x%08X", (0xFF & number));
    }

    public static String toHexStringShort(Integer number) {
        return String.format("%02X", number);
    }

    public static String toHexStringShort(byte number) {
        return String.format("%02X", (0xFF & number));
    }

    /**
     * 
     * @param data  data set you want to search
     * @param value value you want to search for
     * @return index where search value was found
     */
    public static int findInByteArray(byte[] data, byte[] value) {
        return findInByteArray(data, value, 0, data.length);
    }

    /**
     * 
     * @param data          data set you want to search
     * @param value         value you want to search for
     * @param startingPoint index where you want to start your search (inclusive)
     * @return index where search value was found
     */
    public static int findInByteArray(byte[] data, byte[] value, int startingPoint) {
        return findInByteArray(data, value, 0, data.length);
    }

    /**
     * 
     * @param data          data set you want to search
     * @param value         value you want to search for
     * @param startingPoint index where you want to start your search (inclusive)
     * @param endPoint      index where you want to end your search (exclusive)
     * @return index where search value was found
     */
    public static int findInByteArray(byte[] data, byte[] value, int startingPoint, int endPoint) {
        if (startingPoint < 0 || startingPoint > endPoint || endPoint > data.length) {
            return -1;
        }

        if (value.length > endPoint - startingPoint) {
            /*
             * the value we're looking for is larger than the data set
             * so we'll never be able to find it.
             */
            return -1;
        }

        for (int i = startingPoint; i < endPoint; i++) {
            if (i + value.length > endPoint) {
                /*
                 * we're close enough to the end of the main byte array that
                 * that the value were searching for wouldn't fit in the remaining
                 * space. We can just give up at this point.
                 */
                return -1;
            }
            if (value[0] == data[i]) {
                boolean isMatch = true;
                for (int j = 1; j < value.length; j++) {
                    if (value[j] != data[i + j]) {
                        isMatch = false;
                        break;
                    }
                }
                if (isMatch) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static ArrayList<Integer> findAllInByteArray(byte[] data, byte[] value) {
        ArrayList<Integer> matchIndices = new ArrayList<Integer>();

        if (value.length > data.length) {
            /*
             * the value we're looking for is larger than the data set
             * so we'll never be able to find it.
             */
            return matchIndices;
        }

        for (int i = 0; i < data.length; i++) {
            if (i + value.length > data.length) {
                /*
                 * we're close enough to the end of the main byte array that
                 * that the value were searching for wouldn't fit in the remaining
                 * space. We can just give up at this point.
                 */
                break;
            }
            if (value[0] == data[i]) {
                boolean isMatch = true;
                for (int j = 1; j < value.length; j++) {
                    if (value[j] != data[i + j]) {
                        isMatch = false;
                        break;
                    }
                }
                if (isMatch) {
                    matchIndices.add(i);
                }
            }
        }
        return matchIndices;
    }

    /**
     * Digits are used in the context of hex numbers here and not decimal or binary
     * digits.
     * 1 digit = 4 bytes.
     * 
     * 
     * This function behaves sort of like a regex match.
     * If pass something like {(byte)0xFF, (byte)0xE1} as the search value
     * the search will behave like /FFE.$/
     * 
     * @param data
     * @param value
     * @return
     */
    public static ArrayList<Integer> findAllByMostSignificantDigits(byte[] data, byte[] value) {
        ArrayList<Integer> matchIndices = new ArrayList<Integer>();

        if (value.length > data.length) {
            /*
             * the value we're looking for is larger than the data set
             * so we'll never be able to find it.
             */
            return matchIndices;
        }

        for (int i = 0; i < data.length; i++) {
            if (i + value.length > data.length) {
                /*
                 * we're close enough to the end of the main byte array that
                 * that the value were searching for wouldn't fit in the remaining
                 * space. We can just give up at this point.
                 */
                break;
            }
            if (value[0] == data[i]) {
                boolean isMatch = true;
                for (int j = 1; j < value.length; j++) {
                    if (j < value.length - 1) {
                        if (value[j] != data[i + j]) {
                            isMatch = false;
                            break;
                        }
                    } else {
                        /*
                         * we're assuming `value` is one one number and passing a
                         * byte array like {(byte)0xFF, (byte)0xE1} would mean the
                         * number value actually represents is FFE1.
                         * 
                         * the last element of `value` is our 2 least significant digits
                         */

                        int dataElementLeftDigit = data[i + j] >>> 4;
                        int valueElementLeftDigit = value[j] >>> 4;
                        if (dataElementLeftDigit != valueElementLeftDigit) {
                            isMatch = false;
                            break;
                        }
                        ;
                    }
                }
                if (isMatch) {
                    matchIndices.add(i);
                }
            }
        }
        return matchIndices;
    }

    public static byte[] deleteSection(byte[] data, int start, int end) {
        int finalSize = data.length - (end - start);
        byte[] resizedArray = new byte[finalSize];

        int j = 0;
        for (int i = 0; i < data.length; i++) {
            if (i <= start || i > end) {
                resizedArray[j] = data[i];
                j++;
            }
        }

        return resizedArray;
    }

    public static int sumByteSegment(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += (0xFF & data[i]) << (8 * (data.length - 1 - i));
        }
        return sum;
    }

}
