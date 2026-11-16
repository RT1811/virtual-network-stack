package vnet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ProbeCodec {

    private static final int HEADER_SIZE = 20;
    private static final int MAX_PACKET_SIZE = 1200;

    public static byte[] encode(ProbePacket packet) {

        // TODO: Validate payload length.

        ByteBuffer buffer = ByteBuffer.allocate(
                HEADER_SIZE + packet.payload().length
        );

        buffer.order(ByteOrder.BIG_ENDIAN);

        // TODO: Write protocol fields.

        return buffer.array();
    }
}