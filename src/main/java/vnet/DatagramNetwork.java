package vnet;

import java.io.IOException;

public interface DatagramNetwork extends AutoCloseable{

    void send (NodeId destination, byte[] datagram) throws IOException;

    ReceivedDatagram receive() throws IOException;

    ReceivedDatagram receive(long timeoutMillis) throws IOException;

    int maxDatagramSize();

    @Override
    void close() throws IOException;

}
