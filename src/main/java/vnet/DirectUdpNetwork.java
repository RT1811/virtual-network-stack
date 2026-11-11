package vnet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DirectUdpNetwork implements DatagramNetwork {

    private final DatagramSocket socket;

    private final Map<NodeId, InetSocketAddress> peers;
    private final Map<InetSocketAddress, NodeId> reversePeers;

    public DirectUdpNetwork(DatagramSocket socket, Map<NodeId, InetSocketAddress> peers) {
        this.socket = socket;
        this.peers = Map.copyOf(peers);

        Map<InetSocketAddress, NodeId> reverse = new HashMap<>();

        for(var entry: peers.entrySet()) {
            InetSocketAddress endpoint = entry.getValue();

            if (endpoint.isUnresolved()) {
                throw new IllegalArgumentException(
                        "Unresolved peer endpoint: " + endpoint
                );
            }

            NodeId previous = reverse.put(
                    entry.getValue(),
                    entry.getKey()
            );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "Multiple nodes share the same endpoint"
                );
            }
        }

        this.reversePeers = Map.copyOf(reverse);
    }

    @Override
    public void send(NodeId destination, byte[] data)
            throws IOException {

        InetSocketAddress endpoint = peers.get(destination);

        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Unknown destination: " + destination
            );
        }

        if (data.length > maxPayloadSize()) {
            throw new IllegalArgumentException(
                    "Datagram exceeds maximum payload size"
            );
        }

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                endpoint
        );

        socket.send(packet);
    }

    @Override
    public int maxPayloadSize() {
        return 1200;
    }

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public ReceivedDatagram receive() throws IOException {

        while (true) {
            byte[] buffer = new byte[maxPayloadSize() + 1];

            DatagramPacket packet = new DatagramPacket(
                    buffer,
                    buffer.length
            );

            socket.receive(packet);

            if (packet.getLength() > maxPayloadSize()) {
                continue;
            }

            InetSocketAddress sender = new InetSocketAddress(
                    packet.getAddress(),
                    packet.getPort()
            );

            NodeId source = reversePeers.get(sender);

            if (source == null) {
                continue;
            }

            byte[] data = Arrays.copyOf(
                    packet.getData(),
                    packet.getLength()
            );

            return new ReceivedDatagram(source, data);
        }
    }

    @Override
    public ReceivedDatagram receive(long timeoutMillis)
            throws IOException {

        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "Timeout must be positive"
            );
        }

        long timeoutNanos =
                TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        long start = System.nanoTime();

        int originalTimeout = socket.getSoTimeout();

        try {
            while (true) {

                long elapsed = System.nanoTime() - start;
                long remaining = timeoutNanos - elapsed;

                if (remaining <= 0) {
                    throw new SocketTimeoutException(
                            "Receive deadline expired"
                    );
                }

                long remainingMillis =
                        TimeUnit.NANOSECONDS.toMillis(remaining);

                // Round up to avoid 0 (which means infinite wait).
                if (remaining % 1_000_000 != 0) {
                    remainingMillis++;
                }

                int socketTimeout = (int) Math.min(
                        remainingMillis,
                        Integer.MAX_VALUE
                );

                socket.setSoTimeout(socketTimeout);

                byte[] buffer = new byte[maxPayloadSize() + 1];

                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    // A socket timeout might occur before our overall
                    // deadline due to rounding or scheduling.
                    continue;
                }

                // Reject packets received after our deadline.
                if (System.nanoTime() - start >= timeoutNanos) {
                    throw new SocketTimeoutException(
                            "Receive deadline expired"
                    );
                }

                if (packet.getLength() > maxPayloadSize()) {
                    continue;
                }

                InetSocketAddress sender =
                        new InetSocketAddress(
                                packet.getAddress(),
                                packet.getPort()
                        );

                NodeId source = reversePeers.get(sender);

                if (source == null) {
                    continue;
                }

                byte[] data = Arrays.copyOf(
                        packet.getData(),
                        packet.getLength()
                );

                return new ReceivedDatagram(source, data);
            }
        } finally {
            if (!socket.isClosed()) {
                socket.setSoTimeout(originalTimeout);
            }
        }
    }
}
