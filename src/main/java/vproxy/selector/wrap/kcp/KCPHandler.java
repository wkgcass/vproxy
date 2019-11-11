package vproxy.selector.wrap.kcp;

import vproxy.selector.wrap.arqudp.ArqUDPHandler;
import vproxy.selector.wrap.kcp.mock.ByteBuf;
import vproxy.util.ByteArray;
import vproxy.util.Logger;
import vproxy.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class KCPHandler extends ArqUDPHandler {
    private final Kcp kcp;

    protected KCPHandler(Consumer<ByteArrayChannel> emitter, Object identifier) {
        super(emitter);
        this.kcp = new Kcp(0, (data, kcp) -> {
            assert Logger.lowLevelNetDebug("kcp wants to write " + data.chnl.used() + " bytes to " + kcp.getUser());
            assert Logger.lowLevelNetDebugPrintBytes(data.chnl.getBytes(), data.chnl.getReadOff(), data.chnl.getWriteOff());
            emitter.accept(data.chnl);
        });
        this.kcp.setUser(identifier);
    }

    @Override
    public ByteArray parse(ByteArrayChannel buf) throws IOException {
        int ret = kcp.input(new ByteBuf(buf));
        if (ret < 0) {
            throw new IOException("writing from network to kcp failed: " + ret);
        }

        ByteArray array = null;
        while (kcp.canRecv()) {
            List<ByteBuf> arrays = new LinkedList<>();
            ret = kcp.recv(arrays);
            if (ret <= 0) {
                break;
            }
            if (arrays.isEmpty()) {
                break;
            }
            for (ByteBuf b : arrays) {
                ByteArray a = b.chnl.readAll();
                if (array == null) {
                    array = a;
                } else {
                    array = array.concat(a);
                }
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
