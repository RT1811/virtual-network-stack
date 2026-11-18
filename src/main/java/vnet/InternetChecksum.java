package vnet;

public class InternetChecksum {

    /**
     * Computes the 16-bit Internet one's-complement checksum.
     *
     * Interprets consecutive byte pairs as big-endian 16-bit words.
     * If the input length is odd, the final byte is treated as the
     * high byte of a word whose low byte is zero.
     *
     * Uses end-around carry addition and returns the one's complement
     * of the resulting 16-bit sum.
     *
     * When verifying a complete packet that already contains its
     * checksum, a valid checksum produces a result of zero.
     *
     * @param data the bytes over which to calculate the checksum
     * @return the unsigned 16-bit checksum as an int (0-65535)
     */
    public static int compute (byte[] data) {
        long result = 0;

        for (int i = 0; i < data.length; i = i + 2) {
            int high = data[i] & 0xFF;
            int low;
            if (i + 1 == data.length){
                low = 0x00;
            } else {
                low = data[i + 1]  & 0xFF;
            }

            int word = (high << 8) | low;

            result += word;

            result = (result >>> 16) + (result & 0xFFFF);
        }

        result = (~result) & 0xFFFF;

        return (int) result;

    }

    public static void main(String[] args) {
        byte[][] tests = {
                {0x00, 0x01},
                {(byte) 0xFF, (byte) 0xFF},
                {0x01},
                {}
        };

        for (byte[] data : tests) {
            System.out.printf("%04X%n", compute(data));
        }
    }
}
