package io.vproxy.vpacket.tuples;

import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;

import java.util.Objects;

public class SevenTuple {
    public final MacAddress dlSrc;
    public final MacAddress dlDst;
    public final IP nwSrc;
    public final IP nwDst;
    public final int ipProto;
    public final int tpSrc;
    public final int tpDst;

    private final int hashCode;

    public SevenTuple(MacAddress dlSrc, MacAddress dlDst, IP nwSrc, IP nwDst, int ipProto, int tpSrc, int tpDst) {
        this.dlSrc = dlSrc;
        this.dlDst = dlDst;
        this.nwSrc = nwSrc;
        this.nwDst = nwDst;
        this.ipProto = ipProto;
        this.tpSrc = tpSrc;
        this.tpDst = tpDst;

        this.hashCode = Objects.hash(dlSrc, dlDst, nwSrc, nwDst, ipProto, tpSrc, tpDst);
    }

    @Override
    public String toString() {
        return "SevenTuple{" +
            "dlSrc=" + dlSrc +
            ", dlDst=" + dlDst +
            ", nwSrc=" + nwSrc +
            ", nwDst=" + nwDst +
            ", ipProto=" + ipProto +
            ", tpSrc=" + tpSrc +
            ", tpDst=" + tpDst +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SevenTuple that = (SevenTuple) o;
        return ipProto == that.ipProto && tpSrc == that.tpSrc && tpDst == that.tpDst && Objects.equals(dlSrc, that.dlSrc) && Objects.equals(dlDst, that.dlDst) && Objects.equals(nwSrc, that.nwSrc) && Objects.equals(nwDst, that.nwDst);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
