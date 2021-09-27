package io.vproxy.base.selector.wrap.h2streamed;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.arqudp.ArqUDPBasedFDs;
import io.vproxy.base.selector.wrap.streamed.StreamedArqUDPServerFDs;
import io.vproxy.vfd.IPPort;

import java.io.IOException;

public class H2StreamedServerFDs extends StreamedArqUDPServerFDs {
    public H2StreamedServerFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort local) throws IOException {
        super(fds, loop, local, () -> new H2StreamedFDHandler(false));
    }
}
