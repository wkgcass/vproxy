package vproxy.vpacket.conntrack.udp;

import vproxy.base.util.ByteArray;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

import java.util.LinkedList;

public class UdpListenEntry {
    public final IPPort bind;
    private final UdpListenHandler handler;

    public final ReceivingQueue receivingQueue = new ReceivingQueue();
    public final SendingQueue sendingQueue = new SendingQueue();

    private boolean isClosed = false;
    private IPPort connect;

    public UdpListenEntry(IPPort dst, UdpListenHandler handler) {
        this.bind = dst;
        this.handler = handler;
    }

    // filter packets by remote address
    public void setOnlyReceiveFrom(IPPort remote) {
        connect = remote;
    }

    public void destroy() {
        isClosed = true;
    }

    public class ReceivingQueue {
        private final LinkedList<Datagram> q = new LinkedList<>();

        public boolean store(IP src, int srcPort, ByteArray data) {
            if (connect != null) {
                if (!src.equals(connect.getAddress())) {
                    assert Logger.lowLevelDebug("packet ip " + src + " not matching the connected addr " + connect);
                    return false; // drop
                }
                if (srcPort != connect.getPort()) {
                    assert Logger.lowLevelDebug("packet port " + srcPort + " not matching the connected addr " + connect);
                    return false; // drop
                }
            }
            q.add(new Datagram(src, srcPort, data.copy()));
            handler.readable(UdpListenEntry.this);
            return true;
        }

        public Datagram apiRecv() {
            if (q.isEmpty()) {
                return null;
            }
            return q.removeFirst();
        }

        public boolean hasMorePacketsToRead() {
            return !q.isEmpty();
        }
    }

    public class SendingQueue {
        private final LinkedList<Datagram> q = new LinkedList<>();

        public void apiSend(Datagram data) {
            if (isClosed) {
                Logger.error(LogType.IMPROPER_USE, "the udp is closed but still trying to send packets");
                return;
            }
            q.add(data);
        }

        public Datagram fetch() {
            if (q.isEmpty()) {
                return null;
            }
            return q.removeFirst();
        }
    }
}
