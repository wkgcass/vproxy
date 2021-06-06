package vproxy.base.selector.wrap.kcp;

import vproxy.base.selector.wrap.arqudp.ArqUDPHandler;
import vproxy.base.selector.wrap.kcp.mock.ByteBuf;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.base.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class KCPHandler extends ArqUDPHandler {
    public static class KCPOptions {
        // config is copied from kcptun fast3
        // https://github.com/xtaci/kcptun/blob/master/server/main.go#L371

        public boolean nodelay = true;
        public int interval = 10;
        public int resend = 2;
        public boolean nc = true;

        public int sndWnd = 1024;
        public int rcvWnd = 1024;

        public int mtu = 1250;

        // the following are my configurations
        // decrease rto
        public int rxMinRto = 30;

        // alert the kcp every few ms
        public int clockInterval = 10;
    }

    private final Kcp kcp;
    private final KCPOptions opts;
    private boolean isInvalid = false;

    protected KCPHandler(Consumer<ByteArrayChannel> emitter, Object identifier, KCPOptions options) {
        super(emitter);
        this.kcp = new Kcp(0, (data, kcp) -> {
            assert Logger.lowLevelDebug("kcp wants to write " + data.chnl.used() + " bytes to " + kcp.getUser());
            assert Logger.lowLevelNetDebugPrintBytes(data.chnl.getBytes(), data.chnl.getReadOff(), data.chnl.getWriteOff());
            emitter.accept(data.chnl);
        });
        this.kcp.setUser(identifier);
        kcp.setStream(true); // we always use stream mode
        // configure kcp
        kcp.nodelay(options.nodelay, options.interval, options.resend, options.nc);
        kcp.wndsize(options.sndWnd, options.rcvWnd);
        kcp.setMtu(options.mtu);
        kcp.setRxMinrto(options.rxMinRto);
        kcp.setNocwnd(true);
        this.opts = options;
    }

    @Override
    public ByteArray parse(ByteArrayChannel buf) throws IOException {
        assert Logger.lowLevelDebug("inputting into kcp: " + buf.used());
        assert Logger.lowLevelNetDebugPrintBytes(buf.getBytes(), buf.getReadOff(), buf.used());

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
    public int writableLen() {
        int len = opts.sndWnd - kcp.waitSnd();
        return Math.max(len, 0) * (opts.mtu - Kcp.IKCP_OVERHEAD);
    }

    @Override
    public void clock(long ts) throws IOException {
        if (isInvalid) {
            return;
        }
        kcp.update(ts);
        int state = kcp.getState();
        if (state < 0) {
            isInvalid = true;
            assert Logger.lowLevelDebug("kcp connection is invalid, state = " + state);
            throw new IOException("the kcp connection is invalid: " + kcp.getUser());
        }
    }

    @Override
    public int clockInterval() {
        return opts.clockInterval;
    }
}
