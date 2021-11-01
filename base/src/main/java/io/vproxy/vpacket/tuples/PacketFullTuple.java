package io.vproxy.vpacket.tuples;

import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;

import java.util.Objects;

public class PacketFullTuple {
    private final int devin;
    private final MacAddress dlSrc;
    private final MacAddress dlDst;
    private final IP nwSrc;
    private final IP nwDst;
    private final int ipProto;
    private final int tpSrc;
    private final int tpDst;

    private final int hashCode;

    public PacketFullTuple(int devin, MacAddress dlSrc, MacAddress dlDst, IP nwSrc, IP nwDst, int ipProto, int tpSrc, int tpDst) {
        this.devin = devin;
        this.dlSrc = dlSrc;
        this.dlDst = dlDst;
        this.nwSrc = nwSrc;
        this.nwDst = nwDst;
        this.ipProto = ipProto;
        this.tpSrc = tpSrc;
        this.tpDst = tpDst;

        this.hashCode = hashCode();
    }

    @Override
    public String toString() {
        return "PacketFullTuple{" +
            "devin=" + devin +
            ", dlSrc=" + dlSrc +
            ", dlDst=" + dlDst +
            ", nwSrc=" + nwSrc +
            ", nwDst=" + nwDst +
            ", ipProto=" + ipProto +
            ", tpSrc=" + tpSrc +
            ", tpDst=" + tpDst +
            ", hashCode=" + hashCode +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketFullTuple that = (PacketFullTuple) o;
        return devin == that.devin && ipProto == that.ipProto && tpSrc == that.tpSrc && tpDst == that.tpDst && hashCode == that.hashCode && Objects.equals(dlSrc, that.dlSrc) && Objects.equals(dlDst, that.dlDst) && Objects.equals(nwSrc, that.nwSrc) && Objects.equals(nwDst, that.nwDst);
    }

    @Override
    public int hashCode() {
        return Objects.hash(devin, dlSrc, dlDst, nwSrc, nwDst, ipProto, tpSrc, tpDst, hashCode);
    }
}
