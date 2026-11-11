package vnet;

import java.io.IOException;

public interface DatagramNetwork extends AutoCloseable{

    void send (NodeId destination, byte[] data) throws IOException;

    ReceivedDatagram receive() throws IOException;

    ReceivedDatagram receive(long timeoutMillis) throws IOException;

    int maxPayloadSize();

    @Override
    void close() throws IOException;

}
