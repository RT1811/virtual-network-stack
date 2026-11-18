package vnet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternetChecksumTest {

    @Test
    void computesChecksumWithEndAroundCarry() {
        byte[] data = {
                (byte) 0xAB,
                (byte) 0xCD,
                (byte) 0x78,
                (byte) 0x90
        };

        int actual = InternetChecksum.compute(data);

        assertEquals(0xDBA1, actual);
    }

    @ParameterizedTest
    @CsvSource({
            "'00 01', FFFE",
            "'FF FF', 0000",
            "'01', FEFF",
            "'', FFFF"
    })
    void computesKnownChecksums(String hexBytes, String expectedHex) {

        byte[] data = java.util.HexFormat.of()
                .parseHex(hexBytes.replace(" ", ""));

        int expected = Integer.parseInt(expectedHex, 16);

        assertEquals(expected, InternetChecksum.compute(data));
    }
}