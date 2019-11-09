package vproxy.selector.wrap.kcp;

import vproxy.selector.wrap.arqudp.ArqUDPHandler;
import vproxy.selector.wrap.kcp.mock.ByteBuf;
import vproxy.util.ByteArray;
import vproxy.util.Logger;
import vproxy.util.Utils;
import vproxy.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.util.function.Consumer;

public class KCPHandler extends ArqUDPHandler {
    private final Kcp kcp;

    protected KCPHandler(Consumer<ByteArrayChannel> emitter, Object identifier) {
        super(emitter);
        this.kcp = new Kcp(0, (data, kcp) -> {
            assert Logger.lowLevelDebug("kcp wants to write " + data.chnl.used() + " bytes to " + kcp.getUser());
            assert Utils.printBytes(data.chnl.getBytes(), data.chnl.getReadOff(), data.chnl.getWriteOff());
            emitter.accept(data.chnl);
        });
        this.kcp.setUser(identifier);
    }

    @Override
    public ByteArray parse(ByteArrayChannel buf) throws IOException {
        assert Logger.lowLevelDebug("kcp is inputting " + buf.used() + " bytes from " + kcp.getUser());
        assert Utils.printBytes(buf.getBytes(), buf.getReadOff(), buf.getWriteOff());

        int ret = kcp.input(new ByteBuf(buf));
        if (ret < 0) {
            throw new IOException("writing from network to kcp failed: " + ret);
        }

        ByteArray array = null;
        while (true) {
            final int cap = 512;
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(cap);
            ret = kcp.recv(new ByteBuf(chnl));
            assert ret >= -1 || Logger.lowLevelDebug("reading from kcp to app failed: " + ret);
            if (ret <= 0) {
                break;
            }
            ByteArray foo = chnl.getArray().sub(0, ret);
            if (array == null) {
                array = foo;
            } else {
                array = array.concat(foo);
            }
        }
        return array;
    }

    @Override
    public void write(ByteArray input) throws IOException {
        int ret = kcp.send(new ByteBuf(ByteArrayChannel.fromFull(input)));
        if (ret < 0) {
            throw new IOException("writing from app to kcp failed: " + ret);
        }
    }

    @Override
    public void clock(long ts) {
        kcp.update(ts);
    }
}
