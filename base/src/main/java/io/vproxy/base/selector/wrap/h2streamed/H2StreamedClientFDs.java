package io.vproxy.base.selector.wrap.h2streamed;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.arqudp.ArqUDPBasedFDs;
import io.vproxy.base.selector.wrap.streamed.StreamedArqUDPClientFDs;
import io.vproxy.vfd.IPPort;

import java.io.IOException;

public class H2StreamedClientFDs extends StreamedArqUDPClientFDs {
    public H2StreamedClientFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort remote) throws IOException {
        super(fds, loop, remote, () -> new H2StreamedFDHandler(true));
    }
}
