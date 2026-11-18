package vnet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ProbeCodec {

    private static final int HEADER_SIZE = 20;
    private static final int MAX_PACKET_SIZE = 1200;
    private static final int MAGIC = 0x564E4554;
    private static final int VERSION = 1;

    /**
     * Serializes a ProbePacket into the VNET version 1 binary format.
     *
     * Packet layout (big-endian):
     *   0-3:   Magic number ("VNET")
     *   4:     Protocol version
     *   5:     Packet type
     *   6-7:   Payload length (unsigned 16-bit)
     *   8-15:  Request correlation ID
     *   16-17: One's-complement checksum
     *   18-19: Reserved (zero)
     *   20+:   Payload
     *
     * The checksum covers the entire packet, with the checksum
     * field initially set to zero.
     *
     * @param packet the logical probe packet to encode
     * @return the complete encoded packet, including its header
     * @throws IllegalArgumentException if the payload exceeds
     *         the maximum supported size
     */
    public static byte[] encode(ProbePacket packet) {

        byte[] payload = packet.payload();

        if (payload.length > MAX_PACKET_SIZE - HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Probe payload exceeds maximum size"
            );
        }

        ByteBuffer buffer = ByteBuffer.allocate(
                HEADER_SIZE + packet.payload().length
        );

        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) 'V');
        buffer.put((byte) 'N');
        buffer.put((byte) 'E');
        buffer.put((byte) 'T');

        buffer.put((byte) 1); // Protocol version
        buffer.put((byte) packet.type().wireValue());

        buffer.putShort((short) payload.length);
        buffer.putLong(packet.requestId());

        buffer.putShort((short) 0); // Checksum placeholder
        buffer.putShort((short) 0); // Reserved field

        buffer.put(payload);

        byte[] encodedPacket = buffer.array();

        int checksum = InternetChecksum.compute(encodedPacket);

        buffer.putShort(16, (short) checksum);

        return encodedPacket;
    }

    /**
     * Parses and validates a VNET version 1 probe packet.
     *
     * Rejects malformed packets by checking:
     * - Minimum (20 bytes) and maximum packet size (1200 bytes)
     * - Protocol magic and version
     * - Recognized packet type
     * - Reserved field contents
     * - Declared payload length against actual datagram size
     * - One's-complement checksum
     *
     * Only constructs a ProbePacket after validation succeeds.
     *
     * @param datagram the complete encoded probe packet
     * @return the decoded logical probe packet
     * @throws IllegalArgumentException if the packet is malformed,
     *         corrupted, or uses an unsupported protocol version
     */
    public static ProbePacket decode(byte[] datagram) {
        if (datagram == null) {
            throw new IllegalArgumentException("Null packet");
        }

        if (datagram.length < HEADER_SIZE ||
                datagram.length > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid packet size"
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(datagram);
        buffer.order(ByteOrder.BIG_ENDIAN);

        if (buffer.getInt(0) != MAGIC) {
            throw new IllegalArgumentException("Invalid magic");
        }

        int version = Byte.toUnsignedInt(buffer.get(4));

        if (version != VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported protocol version: " + version
            );
        }

        int rawType = Byte.toUnsignedInt(buffer.get(5));

        ProbeType type = ProbeType.fromWireValue(rawType);

        int reserved = Short.toUnsignedInt(buffer.getShort(18));

        if (reserved != 0) {
            throw new IllegalArgumentException(
                    "Reserved field must be zero"
            );
        }

        int payloadLength =
                Short.toUnsignedInt(buffer.getShort(6));

        if (datagram.length != HEADER_SIZE + payloadLength) {
            throw new IllegalArgumentException(
                    "Payload length does not match datagram size"
            );
        }

        if (InternetChecksum.compute(datagram) != 0) {
            throw new IllegalArgumentException(
                    "Invalid packet checksum"
            );
        }

        long requestId = buffer.getLong(8);

        byte[] payload = Arrays.copyOfRange(
                datagram,
                HEADER_SIZE,
                datagram.length
        );

        return new ProbePacket(type, requestId, payload);
    }

    public static void main(String[] args) {
        byte[] payload = "Hello VNET!".getBytes(StandardCharsets.UTF_8);

        ProbePacket original = new ProbePacket(
                ProbeType.REQUEST,
                42L,
                payload
        );

        byte[] encoded = ProbeCodec.encode(original);
        ProbePacket decoded = ProbeCodec.decode(encoded);

        System.out.println(decoded.type());
        System.out.println(decoded.requestId());
        System.out.println(
                new String(decoded.payload(), StandardCharsets.UTF_8)
        );
    }
}