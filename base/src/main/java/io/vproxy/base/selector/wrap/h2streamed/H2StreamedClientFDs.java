package vproxy.base.selector.wrap.h2streamed;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxy.base.selector.wrap.streamed.StreamedArqUDPClientFDs;
import vproxy.vfd.IPPort;

import java.io.IOException;

public class H2StreamedClientFDs extends StreamedArqUDPClientFDs {
    public H2StreamedClientFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort remote) throws IOException {
        super(fds, loop, remote, () -> new H2StreamedFDHandler(true));
    }
}
