package vnet;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ProbeCodecTest {

    @Test
    void roundTripPreservesPacketContents() {
        ProbePacket original = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{10, 20, 30}
        );

        byte[] encoded = ProbeCodec.encode(original);
        ProbePacket decoded = ProbeCodec.decode(encoded);

        assertEquals(original, decoded);
    }

    @Test
    void encodesCorrectBinaryHeader() {
        ProbePacket packet = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{0x01, 0x02}
        );

        byte[] encoded = ProbeCodec.encode(packet);

        assertEquals(22, encoded.length);

        byte[] expectedPrefix = {
                0x56, 0x4E, 0x45, 0x54, // VNET
                0x01,                   // Version
                0x01,                   // REQUEST
                0x00, 0x02,             // Payload length
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x2A  // Request ID 42
        };

        assertArrayEquals(
                expectedPrefix,
                Arrays.copyOfRange(encoded, 0, 16)
        );

        assertArrayEquals(
                new byte[]{0x00, 0x00},
                Arrays.copyOfRange(encoded, 18, 20)
        );

        assertArrayEquals(
                new byte[]{0x01, 0x02},
                Arrays.copyOfRange(encoded, 20, 22)
        );

        assertArrayEquals(
                new byte[]{0x62, 0x2E},
                Arrays.copyOfRange(encoded, 16, 18)
        );

        assertEquals(0, InternetChecksum.compute(encoded));
    }

    @Test
    void rejectsInvalidMagic() {
        ProbePacket packet = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{0x01, 0x02}
        );

        byte[] encoded = ProbeCodec.encode(packet);

        encoded[0] = 0x00;

        assertThrows(
                IllegalArgumentException.class,
                () -> ProbeCodec.decode(encoded)
        );
    }

    @Test
    void rejectsUnsupportedVersion() {
        ProbePacket packet = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{0x01, 0x02}
        );

        byte[] encoded = ProbeCodec.encode(packet);

        // changing the version from 1 to 2; 2 is unsupported.
        encoded[4] = 0x02;

        // Clearing the checksum
        refreshChecksum(encoded);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProbeCodec.decode(encoded)
        );

        assertEquals(
                "Unsupported protocol version: 2",
                exception.getMessage()
        );
    }

    @Test
    void rejectsUnknownPacketType() {
        ProbePacket packet = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{0x01, 0x02}
        );

        byte[] encoded = ProbeCodec.encode(packet);

        // Unknown probe type: Currently only 0x01 and 0x02 exist
        encoded[5] = 0x07;

        // Clearing the checksum
        refreshChecksum(encoded);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProbeCodec.decode(encoded)
        );

        assertEquals(
                "Unknown probe type: 7",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMismatchedPayloadLength() {
        ProbePacket packet = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{0x01, 0x02}
        );

        byte[] encoded = ProbeCodec.encode(packet);

        // changing the length field to 10
        ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort(6, (short) 10);

        // Clearing the checksum
        refreshChecksum(encoded);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProbeCodec.decode(encoded)
        );

        assertEquals(
                "Payload length does not match datagram size",
                exception.getMessage()
        );
    }

    @Test
    void rejectsCorruptedPayload() {
        ProbePacket packet = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[]{0x01, 0x02}
        );

        byte[] encoded = ProbeCodec.encode(packet);

        // Corrupting the payload
        encoded[20] = 0x10;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProbeCodec.decode(encoded)
        );

        assertEquals(
                "Invalid packet checksum",
                exception.getMessage()
        );
    }

    @Test
    void supportsEmptyPayload() {
        ProbePacket original = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                new byte[0]
        );

        byte[] encoded = ProbeCodec.encode(original);
        ProbePacket decoded = ProbeCodec.decode(encoded);

        assertEquals(20, encoded.length);
        assertEquals(original, decoded);
    }

    @Test
    void enforcesMaximumPayloadSize() {
        byte[] payload = new byte[1180];
        Arrays.fill(payload, (byte) 0xAB);

        ProbePacket valid = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                payload
        );

        byte[] encoded = ProbeCodec.encode(valid);

        assertEquals(1200, encoded.length);
        assertEquals(valid, ProbeCodec.decode(encoded));

        ProbePacket oversized = new ProbePacket(
                ProbeType.REQUEST,
                43L,
                new byte[1181]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProbeCodec.encode(oversized)
        );
    }

    private static void refreshChecksum(byte[] encoded) {
        encoded[16] = 0;
        encoded[17] = 0;

        int checksum = InternetChecksum.compute(encoded);

        ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort(16, (short) checksum);
    }
}