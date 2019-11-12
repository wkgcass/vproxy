package vproxy.selector.wrap.h2streamed;

import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxy.selector.wrap.streamed.StreamedArqUDPServerFDs;

import java.io.IOException;
import java.net.InetSocketAddress;

public class H2StreamedServerFDs extends StreamedArqUDPServerFDs {
    public H2StreamedServerFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, InetSocketAddress local) throws IOException {
        super(fds, loop, local, () -> new H2StreamedFDHandler(false));
    }
}
