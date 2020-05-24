package vproxybase.selector.wrap.h2streamed;

import vfd.IPPort;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxybase.selector.wrap.streamed.StreamedArqUDPClientFDs;

import java.io.IOException;

public class H2StreamedClientFDs extends StreamedArqUDPClientFDs {
    public H2StreamedClientFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort remote) throws IOException {
        super(fds, loop, remote, () -> new H2StreamedFDHandler(true));
    }
}
