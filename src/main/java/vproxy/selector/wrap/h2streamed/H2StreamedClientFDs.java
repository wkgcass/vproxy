package vproxy.selector.wrap.h2streamed;

import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxy.selector.wrap.streamed.StreamedArqUDPClientFDs;

import java.io.IOException;
import java.net.InetSocketAddress;

public class H2StreamedClientFDs extends StreamedArqUDPClientFDs {
    public H2StreamedClientFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, InetSocketAddress remote) throws IOException {
        super(fds, loop, remote, () -> new H2StreamedFDHandler(true));
    }
}
