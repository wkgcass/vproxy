package io.vproxy.base.selector.wrap.quic;

import io.vproxy.base.selector.wrap.AbstractDelegateSocketFD;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IPPort;

import java.io.IOException;

public class QuicDelegateSocketFD extends AbstractDelegateSocketFD {
    private final QuicFDs fds;

    public QuicDelegateSocketFD(QuicFDs fds) {
        this.fds = fds;
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        fds.lookupOrCreateConnection(l4addr,
            conn -> {
                QuicSocketFD fd;
                try {
                    fd = QuicSocketFD.newStream(fds.isWithLog(), conn.getConnection());
                } catch (IOException e) {
                    assert Logger.lowLevelDebug(STR."failed to create stream: \{e}");
                    raiseError(e);
                    return;
                }

                fd.connect(l4addr);
                assert Logger.lowLevelDebug(STR."delegating source fd is ready now: \{fd}");
                setDelegatingSourceFD(fd);
            },
            _ -> raiseError(new IOException("connection shutdown")));
    }
}
