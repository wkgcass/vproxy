package io.vproxy.base.util.net;

import io.vproxy.base.connection.Protocol;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IPPort;

import java.util.*;

public class SNatIPPortPool {
    private final IPPortPool pool;
    private final Set<Tuple> ctSet = new HashSet<>();
    private final Map<IPPort, Ref> srcRefMap = new HashMap<>();
    private final ArrayList<IPPort> srcList = new ArrayList<>();

    public SNatIPPortPool(String expression) {
        this.pool = new IPPortPool(expression);
    }

    public IPPort allocate(Protocol protocol, IPPort target) {
        Tuple tuple = new Tuple();
        if (protocol == Protocol.TCP) {
            tuple.proto = Consts.IP_PROTOCOL_TCP;
        } else if (protocol == Protocol.UDP) {
            tuple.proto = Consts.IP_PROTOCOL_UDP;
        } else {
            throw new IllegalArgumentException("unsupported protocol " + protocol);
        }
        tuple.dst = target;

        IPPort selected = null;
        for (int i = srcList.size() - 1; i >= 0; --i) {
            IPPort src = srcList.get(i);
            tuple.src = src;
            tuple.clearHash();
            if (!ctSet.contains(tuple)) {
                selected = src;
                break;
            }
        }

        if (selected == null) {
            selected = pool.allocate();
            if (selected != null) {
                tuple.src = selected;
                tuple.clearHash();
                srcList.add(selected);
            }
        }
        if (selected == null) {
            return null;
        }

        var ref = srcRefMap.get(selected);
        if (ref == null) {
            ref = new Ref();
            srcRefMap.put(selected, ref);
        }
        ref.ref += 1;

        tuple.srcRef = ref;

        ctSet.add(tuple);

        return selected;
    }

    public void release(Protocol protocol, IPPort src, IPPort dst) {
        var tuple = new Tuple();
        if (protocol == Protocol.TCP) {
            tuple.proto = Consts.IP_PROTOCOL_TCP;
        } else if (protocol == Protocol.UDP) {
            tuple.proto = Consts.IP_PROTOCOL_UDP;
        } else {
            throw new IllegalArgumentException("unsupported protocol " + protocol);
        }
        tuple.src = src;
        tuple.dst = dst;

        var removed = ctSet.remove(tuple);
        if (!removed) {
            return;
        }

        var ref = srcRefMap.get(src);
        if (ref == null) { // should not happen, but we check anyway
            return;
        }
        ref.ref -= 1;
        if (ref.ref != 0) {
            return;
        }

        srcRefMap.remove(src);
        srcList.remove(src);

        pool.release(src);
    }

    @Override
    public String toString() {
        return "SNatIPPortPool{" +
            "pool=" + pool +
            ", ctSet=" + ctSet +
            ", srcRefMap=" + srcRefMap +
            ", srcList=" + srcList +
            '}';
    }

    public String serialize() {
        return pool.serialize();
    }

    private static final class Tuple {
        int proto;
        IPPort src;
        IPPort dst;

        // not used in equals/hashCode
        Ref srcRef;
        int hash;

        void clearHash() {
            hash = 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tuple tuple = (Tuple) o;
            return proto == tuple.proto && src.equals(tuple.src) && dst.equals(tuple.dst);
        }

        @Override
        public int hashCode() {
            if (hash != 0) return hash;
            hash = Objects.hash(proto, src, dst);
            return hash;
        }
    }

    private static final class Ref {
        int ref = 0;
    }
}
