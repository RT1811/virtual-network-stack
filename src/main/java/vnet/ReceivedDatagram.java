package vnet;

public record ReceivedDatagram (
        NodeId source,
        byte[] datagram
) {}
