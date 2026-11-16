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

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}