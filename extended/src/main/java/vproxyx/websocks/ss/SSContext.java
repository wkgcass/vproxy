package vproxyx.websocks.ss;

import vproxy.socks.AddressType;
import vproxybase.util.RingBuffer;
import vproxybase.util.nio.ByteArrayChannel;

public class SSContext {
    int state = 0;
    // 0 -> expecting req type
    // 1 -> expecting address
    // 2 -> expecting port
    // 3 -> done

    AddressType reqType; // 0x01: ipv4, 0x03: domain, 0x04: ipv6
    int addressLeft; // first set to 4(ipv4)or16(ipv6)or user specific(domain), then self decrease until 0
    byte[] address;
    int portLeft = 2;
    byte[] portBytes = new byte[2];
    int port;

    final RingBuffer inBuffer;
    private final byte[] b = new byte[1];
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(b);

    public SSContext(RingBuffer inBuffer) {
        this.inBuffer = inBuffer;
    }

    boolean hasNext() {
        return inBuffer.used() > 0;
    }

    byte next() {
        chnl.reset();
        inBuffer.writeTo(chnl);
        return b[0];
    }
}
