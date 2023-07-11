package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.connection.Protocol;
import io.vproxy.base.util.Timer;
import io.vproxy.base.util.anno.Nullable;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.net.SNatIPPortPool;
import io.vproxy.vpacket.conntrack.Conntrack;

public class TcpNat extends Tuple<TcpEntry, TcpEntry> {
    @Nullable public final SNatIPPortPool pool;
    private final boolean releaseIp;
    public final Conntrack conntrack;
    private final Timer timer;
    private final TcpTimeout timeout;

    private TcpState state = TcpState.CLOSED;
    private boolean isDestroyed = false;

    public ProxyProtocolHelper proxyProtocolHelper = null;

    public TcpNat(TcpEntry _1, TcpEntry _2,
                  Conntrack conntrack, TcpTimeout timeout) {
        this(_1, _2, null, false, conntrack, timeout);
    }

    public TcpNat(TcpEntry _1, TcpEntry _2,
                  @Nullable SNatIPPortPool pool, boolean releaseIp,
                  Conntrack conntrack, TcpTimeout timeout) {
        super(_1, _2);
        this.releaseIp = releaseIp;
        this.pool = pool;
        this.conntrack = conntrack;
        this.timeout = timeout;

        this.timer = new Timer(conntrack.loop, timeout.getClose() * 1000) {
            @Override
            public void cancel() {
                super.cancel();
                destroy();
            }
        };
        this.timer.start();
    }

    public TcpState getState() {
        return state;
    }

    public void setState(TcpState state) {
        this.state = state;
        if (isDestroyed) {
            return;
        }
        int seconds = timeout.getClose();
        switch (state) {
            case SYN_SENT:
                seconds = (timeout.getSynSent());
                break;
            case SYN_RECEIVED:
                seconds = (timeout.getSynRecv());
                break;
            case ESTABLISHED:
                seconds = (timeout.getEstablished());
                break;
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSING:
                seconds = (timeout.getFinWait());
                break;
            case CLOSE_WAIT:
                seconds = (timeout.getCloseWait());
                break;
            case LAST_ACK:
                seconds = (timeout.getLastAck());
                break;
            case TIME_WAIT:
                seconds = (timeout.getTimeWait());
                break;
        }
        timer.setTimeout(seconds * 1000);
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
        conntrack.removeTcp(_1.remote, _1.local);
        conntrack.removeTcp(_2.remote, _2.local);

        if (releaseIp && pool != null) {
            pool.release(Protocol.TCP, _2.local, _2.remote);
        }
    }

    @Override
    public String toString() {
        return "TcpNat{" +
            "active=" + _1.description() +
            ", passive=" + _2.description() +
            ", state=" + state +
            ", timer=" + timer.getTTL() +
            ", pp=" + proxyProtocolHelper +
            '}';
    }
}
