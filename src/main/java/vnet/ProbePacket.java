package vnet;

import java.util.Arrays;
import java.util.Objects;

public record ProbePacket(
        ProbeType type,
        long requestId,
        byte[] payload
) {
    public ProbePacket {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        payload = Arrays.copyOf(payload, payload.length);
    }

    /**
     * Returns the payload bytes carried by this packet.
     *
     * <p>The returned array is a copy: modifying it does not affect this packet,
     * and later changes to the array originally passed to the constructor do not
     * affect it either.
     *
     * @return a new array containing the payload; empty (never null) if the
     *         packet carries no payload
     */
    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProbePacket other
                && type == other.type
                && requestId == other.requestId
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(type, requestId) + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "ProbePacket[type=" + type + ", requestId=" + requestId
                + ", payloadLength=" + payload.length + "]";
    }
}