package vproxybase.selector.wrap.h2streamed;

import vfd.IPPort;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxybase.selector.wrap.streamed.StreamedArqUDPServerFDs;

import java.io.IOException;

public class H2StreamedServerFDs extends StreamedArqUDPServerFDs {
    public H2StreamedServerFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort local) throws IOException {
        super(fds, loop, local, () -> new H2StreamedFDHandler(false));
    }
}
