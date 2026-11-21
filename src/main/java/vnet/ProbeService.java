package vnet;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ProbeService {

    private final DatagramNetwork network;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ProbeService(DatagramNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    /**
     * Sends a probe and waits for a matching response until the deadline.
     *
     * @param destination the node to probe
     * @return true if a matching response arrives, false if the probe times out
     * @throws IOException if network communication fails
     */
    public boolean runProbe(
            NodeId destination
    ) throws IOException {

        ProbePacket request = new ProbePacket(
                ProbeType.REQUEST,
                RANDOM.nextLong(),
                "Hello VNET!".getBytes(StandardCharsets.UTF_8)
        );

        byte[] encodedDatagram = ProbeCodec.encode(request);

        network.send(destination, encodedDatagram);

        // One deadline governs the entire probe, including ignored responses.
        long timeoutNanos = TimeUnit.SECONDS.toNanos(2);
        long start = System.nanoTime();

        try {
            while (true) {
                long elapsed = System.nanoTime() - start;
                long remaining = timeoutNanos - elapsed;

                if (remaining <= 0) {
                    throw new SocketTimeoutException(
                            "Probe deadline expired"
                    );
                }

                long remainingMillis =
                        TimeUnit.NANOSECONDS.toMillis(remaining);

                if (remaining % 1_000_000 != 0) {
                    remainingMillis++;
                }

                ReceivedDatagram received =
                        network.receive(remainingMillis);

                ProbePacket response;

                try {
                    response = ProbeCodec.decode(received.datagram());
                } catch (IllegalArgumentException e) {
                    System.out.println(
                            "Ignoring malformed packet: " + e.getMessage()
                    );
                    continue;
                }

                // Only accept the response belonging to this probe.
                if (!isMatchingResponse(
                        received,
                        response,
                        destination,
                        request.requestId()
                )) {
                    continue;
                }

                System.out.println("Valid response received: " + response);

                break;
            }

            return true;

        } catch (SocketTimeoutException e) {
            System.out.println(
                    "Probe timed out: no matching response received"
            );
            return false;
        }
    }

    /**
     * Checks whether a decoded packet is the expected response
     * to the current probe.
     *
     * A response must come from the intended peer, have type
     * RESPONSE, and carry the original request correlation ID.
     *
     * @param received the datagram containing the packet
     * @param response the successfully decoded probe packet
     * @param destination the expected responding node
     * @param requestId the outstanding request's correlation ID
     * @return true if the packet matches the current probe
     */
    private static boolean isMatchingResponse(
            ReceivedDatagram received,
            ProbePacket response,
            NodeId destination,
            long requestId
    ) {
        if (!received.source().equals(destination)) {
            System.out.println("Ignoring unexpected peer");
            return false;
        }

        if (response.type() != ProbeType.RESPONSE) {
            System.out.println("Ignoring non-response packet");
            return false;
        }

        if (response.requestId() != requestId) {
            System.out.println(
                    "Ignoring response: expected requestId="
                            + requestId
                            + ", received="
                            + response.requestId()
            );
            return false;
        }

        return true;
    }

    /**
     * Continuously receives and processes probe requests.
     *
     * Malformed packets are discarded without terminating the listener.
     * Only valid REQUEST packets generate responses; RESPONSE packets
     * are ignored to prevent response loops.
     *
     * @throws IOException if the underlying network encounters an I/O failure
     */
    public void runListener() throws IOException {

        while (true) {
            ReceivedDatagram received = network.receive();

            ProbePacket packet;

            try {
                packet = ProbeCodec.decode(received.datagram());
            } catch (IllegalArgumentException e) {
                System.out.println(
                        "Ignoring malformed packet: " + e.getMessage()
                );
                continue;
            }

            // Only requests should generate responses.
            if (packet.type() != ProbeType.REQUEST) {
                System.out.println("Ignoring non-request packet");
                continue;
            }

            System.out.println("Received from: " + received.source());
            System.out.println("Packet: " + packet);
            System.out.println(
                    "Payload: " +
                            new String(packet.payload(), StandardCharsets.UTF_8)
            );

            // Echo the request ID and payload so the sender can
            // correlate the response with its original request.
            ProbePacket response = new ProbePacket(
                    ProbeType.RESPONSE,
                    packet.requestId(),
                    packet.payload()
            );

            byte[] encodedResponse = ProbeCodec.encode(response);

            network.send(received.source(), encodedResponse);

            System.out.println(
                    "Response sent to " + received.source() + "\n"
            );
        }
    }

}