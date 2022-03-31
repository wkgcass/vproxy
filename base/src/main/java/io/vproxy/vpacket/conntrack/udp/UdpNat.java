package io.vproxy.vpacket.conntrack.udp;

import io.vproxy.base.util.Timer;
import io.vproxy.base.util.anno.Nullable;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.net.IPPortPool;
import io.vproxy.vpacket.conntrack.Conntrack;

public class UdpNat extends Tuple<UdpEntry, UdpEntry> {
    @Nullable public final IPPortPool pool;
    private final boolean releaseIp;
    public final Conntrack conntrack;
    private final Timer timer;

    private boolean isDestroyed = false;

    public UdpNat(UdpEntry _1, UdpEntry _2,
                  Conntrack conntrack, int timeout) {
        this(_1, _2, null, false, conntrack, timeout);
    }

    public UdpNat(UdpEntry _1, UdpEntry _2,
                  @Nullable IPPortPool pool, boolean releaseIp,
                  Conntrack conntrack, int timeout) {
        super(_1, _2);
        this.releaseIp = releaseIp;
        this.pool = pool;
        this.conntrack = conntrack;

        this.timer = new Timer(conntrack.loop, timeout * 1000) {
            @Override
            public void cancel() {
                super.cancel();
                destroy();
            }
        };
    }

    public void resetTimer() {
        if (isDestroyed) {
            return;
        }
        timer.resetTimer();
    }

    public long getTTL() {
        return timer.getTTL();
    }

    public void destroy() {
        if (isDestroyed) {
            return;
        }
        isDestroyed = true;

        timer.cancel();
        conntrack.removeUdp(_1.remote, _1.local);
        conntrack.removeUdp(_2.remote, _2.local);

        if (releaseIp && pool != null) {
            pool.release(_2.local);
        }
    }

    @Override
    public String toString() {
        return "UdpNat{" +
            "active=" + _1 +
            ", passive=" + _2 +
            ", timer=" + timer.getTTL() +
            '}';
    }
}
