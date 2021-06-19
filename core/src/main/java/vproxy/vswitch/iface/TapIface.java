package vproxy.vswitch.iface;

import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.blocking.BlockingDatagramFD;
import vproxy.base.util.*;
import vproxy.base.util.exception.XException;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.*;
import vproxy.vswitch.SocketBuffer;
import vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class TapIface extends AbstractIface implements Iface {
    public final String devPattern;
    private TapDatagramFD tap;
    public final int localSideVni;
    public final String postScript;
    public final Annotations annotations;

    private AbstractDatagramFD<?> operateTap;
    private SelectorEventLoop bondLoop;

    private final ByteBuffer sndBuf = ByteBuffer.allocateDirect(2048);

    public TapIface(String devPattern,
                    int localSideVni,
                    String postScript,
                    Annotations annotations) {
        this.devPattern = devPattern;
        this.localSideVni = localSideVni;
        this.postScript = postScript;
        if (annotations == null) {
            annotations = new Annotations();
        }
        this.annotations = annotations;
    }

    public TapDatagramFD getTap() {
        return tap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TapIface tapIface = (TapIface) o;
        return Objects.equals(tap, tapIface.tap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tap);
    }

    @Override
    public String toString() {
        return "Iface(tap:" + tap.getTap().dev + ",vni:" + localSideVni + ")";
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);

        bondLoop = params.loop;
        FDs fds = FDProvider.get().getProvided();
        FDsWithTap tapFDs = (FDsWithTap) fds;
        tap = tapFDs.openTap(devPattern);
        try {
            if (tapFDs.tapNonBlockingSupported()) {
                operateTap = tap;
                tap.configureBlocking(false);
            } else {
                operateTap = new BlockingDatagramFD<>(tap, bondLoop, 2048, 65536, 32);
            }
            bondLoop.add(operateTap, EventSet.read(), null, new TapHandler());
        } catch (IOException e) {
            if (operateTap != null) {
                try {
                    operateTap.close();
                    operateTap = null;
                } catch (IOException t) {
                    Logger.shouldNotHappen("failed to close the tap device wrapper when rolling back the creation", t);
                }
            }
            try {
                tap.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tap device when rolling back the creation", t);
            }
            throw e;
        }

        try {
            SwitchUtils.executeTapPostScript(params.sw.alias, tap.getTap().dev, localSideVni, postScript);
        } catch (Exception e) {
            // executing script failed
            // close the fds
            try {
                bondLoop.remove(operateTap);
            } catch (Throwable ignore) {
            }
            try {
                operateTap.close();
                operateTap = null;
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tap device wrapper when rolling back the creation", t);
            }
            try {
                tap.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("closing the tap fd failed, " + tap, t);
            }
            throw new XException(Utils.formatErr(e));
        }
    }

    @Override
    public void sendPacket(SocketBuffer skb) {
        sndBuf.position(0).limit(sndBuf.capacity());
        var bytes = skb.pkt.getRawPacket().toJavaArray();
        sndBuf.put(bytes);
        sndBuf.flip();
        try {
            operateTap.write(sndBuf);
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "sending packet to " + this + " failed", e);
        }
    }

    @Override
    public void destroy() {
        if (operateTap != null) {
            try {
                bondLoop.remove(operateTap);
            } catch (Throwable ignore) {
            }
            try {
                operateTap.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing tap device failed", e);
            }
        }
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localSideVni;
    }

    @Override
    public int getOverhead() {
        return 0;
    }

    private class TapHandler implements Handler<AbstractDatagramFD<?>> {
        private static final int TOTAL_LEN = SwitchUtils.TOTAL_RCV_BUF_LEN;
        private static final int PRESERVED_LEN = SwitchUtils.RCV_HEAD_PRESERVE_LEN;

        private final TapIface iface = TapIface.this;
        private final TapDatagramFD tapDatagramFD = TapIface.this.tap;

        private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(TOTAL_LEN);
        private final ByteArray raw = ByteArray.from(rcvBuf.array());

        @Override
        public void accept(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<AbstractDatagramFD<?>> ctx) {
            while (true) {
                VProxyThread.current().newUuidDebugInfo();

                rcvBuf.limit(TOTAL_LEN).position(PRESERVED_LEN);
                try {
                    ctx.getChannel().read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "tap device " + tapDatagramFD + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == PRESERVED_LEN) {
                    break; // nothing read, quit loop
                }
                SocketBuffer skb = SocketBuffer.fromEtherBytes(iface, localSideVni, raw, PRESERVED_LEN, TOTAL_LEN - rcvBuf.position());
                String err = skb.init();
                if (err != null) {
                    assert Logger.lowLevelDebug("got invalid packet: " + err);
                    continue;
                }

                received(skb);
                callback.alertPacketsArrive();
            }
        }

        @Override
        public void writable(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // ignore, and will not fire
        }

        @Override
        public void removed(HandlerContext<AbstractDatagramFD<?>> ctx) {
            Logger.warn(LogType.CONN_ERROR, "tap device " + tapDatagramFD + " removed from loop, it's not handled anymore, need to be closed");
            callback.alertDeviceDown(iface);
        }
    }
}
