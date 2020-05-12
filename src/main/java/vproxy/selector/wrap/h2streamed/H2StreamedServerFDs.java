package vproxy.selector.wrap.h2streamed;

import vfd.IPPort;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxy.selector.wrap.streamed.StreamedArqUDPServerFDs;

import java.io.IOException;

public class H2StreamedServerFDs extends StreamedArqUDPServerFDs {
    public H2StreamedServerFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort local) throws IOException {
        super(fds, loop, local, () -> new H2StreamedFDHandler(false));
    }
}
