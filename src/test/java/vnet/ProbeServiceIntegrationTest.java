package vnet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProbeServiceIntegrationTest {

    @Test
    void probeRoundTripOverUdp() throws Exception {

        try (DatagramSocket socketA =
                     new DatagramSocket(0, InetAddress.getLoopbackAddress());
             DatagramSocket socketB =
                     new DatagramSocket(0, InetAddress.getLoopbackAddress())) {

            int portA = socketA.getLocalPort();
            int portB = socketB.getLocalPort();

            assertNotEquals(portA, portB);

            NodeId nodeA = new NodeId(1);
            NodeId nodeB = new NodeId(2);

            InetAddress loopback = InetAddress.getLoopbackAddress();

            Map<NodeId, InetSocketAddress> peersA = Map.of(
                    nodeB, new InetSocketAddress(loopback, portB)
            );

            Map<NodeId, InetSocketAddress> peersB = Map.of(
                    nodeA, new InetSocketAddress(loopback, portA)
            );

            try (DirectUdpNetwork networkA = new DirectUdpNetwork(socketA, peersA);
                 DirectUdpNetwork networkB = new DirectUdpNetwork(socketB, peersB)) {

                assertEquals(1200, networkA.maxDatagramSize());
                assertEquals(1200, networkB.maxDatagramSize());

                ProbeService serviceA = new ProbeService(networkA);
                ProbeService serviceB = new ProbeService(networkB);

                AtomicReference<Throwable> listenerFailure = new AtomicReference<>();

                Thread listenerThread = new Thread(() -> {
                    try {
                        serviceB.runListener();
                    } catch (IOException e) {
                        if (!socketB.isClosed()) {
                            listenerFailure.set(e);
                        }
                    } catch (Throwable t) {
                        listenerFailure.set(t);
                    }
                });

                listenerThread.start();

                try {
                    assertTrue(
                            serviceA.runProbe(nodeB),
                            "Node A should receive a matching probe response"
                    );
                } finally {
                    networkB.close();
                    listenerThread.join(1000);
                }

                assertFalse(listenerThread.isAlive(), "Listener should stop after socket closure");
                assertNull(listenerFailure.get(), "Listener should not fail unexpectedly");
            }
        }
    }

    @Test
    void probeTimesOutWhenPeerDoesNotRespond() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();

        try (DatagramSocket socketA = new DatagramSocket(0, loopback);
             DatagramSocket socketB = new DatagramSocket(0, loopback)) {

            NodeId nodeB = new NodeId(2);

            Map<NodeId, InetSocketAddress> peersA = Map.of(
                    nodeB,
                    new InetSocketAddress(loopback, socketB.getLocalPort())
            );

            try (DirectUdpNetwork networkA =
                         new DirectUdpNetwork(socketA, peersA)) {

                ProbeService serviceA = new ProbeService(networkA);

                assertFalse(
                        serviceA.runProbe(nodeB),
                        "Probe should time out when no response arrives"
                );
            }
        }
    }

    @Test
    void unicodePayloadRoundTrip() {
        byte[] payload = "Hello 世界 🌍"
                .getBytes(StandardCharsets.UTF_8);

        ProbePacket original = new ProbePacket(
                ProbeType.REQUEST,
                12345L,
                payload
        );

        byte[] encoded = ProbeCodec.encode(original);
        ProbePacket decoded = ProbeCodec.decode(encoded);

        assertEquals(original, decoded);
    }
}