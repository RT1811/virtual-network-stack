package vnet;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;

public class NodeMain {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("Usage: listen | send");
            return;
        }

        NodeId nodeA = new NodeId(1);
        NodeId nodeB = new NodeId(2);

        Map<NodeId, InetSocketAddress> peers = Map.of(
                nodeA, new InetSocketAddress("127.0.0.1", 9001),
                nodeB, new InetSocketAddress("127.0.0.1", 9002)
        );

        switch (args[0]) {

            case "listen" -> {
                DatagramSocket socket = new DatagramSocket(9002);

                try (DirectUdpNetwork network =
                             new DirectUdpNetwork(socket, peers)) {

                    System.out.println("Node B listening on port 9002");
                    ProbeService service = new ProbeService(network);
                    service.runListener();
                }
            }

            case "send" -> {
                DatagramSocket socket = new DatagramSocket(9001);

                try (DirectUdpNetwork network =
                             new DirectUdpNetwork(socket, peers)) {

                    ProbeService service = new ProbeService(network);
                    service.runProbe(nodeB);
                }
            }

            default -> System.out.println("Unknown mode");
        }
    }

}