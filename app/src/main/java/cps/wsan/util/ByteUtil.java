package cps.wsan.util;

/**
 * A helper class that contains many utility methods for encoding and decoding data to and from
 * bytes.
 */
public class ByteUtil {

    /**
     * Compress data of different bit lengths into multiple integers.
     * @param size      The size of the returned array (sum of lengths as multiple of 32)
     * @param data      The data that should be compressed
     * @param lengths   The length of the bits corresponding to the given data
     * @return A compressed packet as an integer array
     */
    public static int[] compress(int size, int[] data, int[] lengths) {
        int[] res = new int[size];

        int pos = 0;
        for (int i = 0; i < data.length; i++) {
            int iOff = pos / 32;
            int bOff = pos % 32;

            res[iOff] |= insert(res[iOff], data[i], lengths[i], bOff);
            pos += lengths[i];
        }

        return res;
    }

    /**
     * Inserts bits into an integer. Exclusively inverts those bits that have to be inserted, other
     * bits will be ignored
     * @param old       The integer in which the bits should be inserted
     * @param data      The bits to be inserted in the integer
     * @param length    The amount of bits of data that should be inserted
     * @param offset    An offset indicating in which position the bits should be inserted (offset
     *                  from MSB)
     * @return The newly formed data
     */
    public static int insert(int old, int data, int length, int offset) {
        data = data << (32 - length);
        data = data >>> offset;
        return old | data;
    }

    public static byte[] splitToBytes(int integer) {
        byte[] res = new byte[4];
        res[0] = (byte) ((integer & 0xFF000000) >>> 24);
        res[1] = (byte) ((integer & 0x00FF0000) >>> 16);
        res[2] = (byte) ((integer & 0x0000FF00) >>> 8);
        res[3] = (byte) (integer & 0x000000FF);
        return res;
    }

    public static byte[] splitToBytes(int[] integers) {
        byte[] res = new byte[integers.length * 4];

        for (int i = 0; i < integers.length; i++) {
            byte[] tmp = splitToBytes(integers[i]);
            res[i * 4] = tmp[0];
            res[i * 4 + 1] = tmp[1];
            res[i * 4 + 2] = tmp[2];
            res[i * 4 + 3] = tmp[3];
        }

        return res;
    }

    public static int mergeToInteger(byte[] bytes) {
        if (bytes.length != 4)
            throw new IllegalArgumentException("Only accepts byte arrays of length 4");

        int res = 0;
        res |= 0xFF000000 & ((int) bytes[0] << 24);
        res |= 0x00FF0000 & ((int) bytes[1] << 16);
        res |= 0x0000FF00 & ((int) bytes[2] << 8);
        res |= 0x000000FF & ((int) bytes[3]);

        return res;
    }

    public static int[] mergeToIntegers(byte[] bytes) {
        int[] res = new int[bytes.length / 4 + (bytes.length % 4 != 0 ? 1 : 0)];

        for (int i = 0; i < res.length; i++) {
            try {
                res[i] = mergeToInteger(new byte[]{
                        bytes[i * 4], bytes[i * 4 + 1], bytes[i * 4 + 2], bytes[i * 4 + 3]});
            } catch (ArrayIndexOutOfBoundsException e) {
                res[i] = 0;
            }
        }

        return res;
    }

    public static int selectBits(int data, int offset, int length) {
        if (length + offset > 32)
            throw new IllegalArgumentException("Cannot select data outside of integer range");

        data = data << offset;
        data = data >>> (32 - length);
        return data;
    }

}
