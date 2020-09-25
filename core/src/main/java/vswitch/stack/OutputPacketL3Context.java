package vswitch.stack;

import vpacket.AbstractIpPacket;
import vswitch.Table;

public class OutputPacketL3Context {
    public final String handlingUUID;
    public final Table table;
    public final AbstractIpPacket outputPacket;

    public OutputPacketL3Context(String handlingUUID,
                                 Table table,
                                 AbstractIpPacket outputPacket) {
        this.handlingUUID = handlingUUID;
        this.table = table;
        this.outputPacket = outputPacket;
    }

    @Override
    public String toString() {
        return "OutputPacketL3Context{" +
            "handlingUUID='" + handlingUUID + '\'' +
            ", table=" + table.vni +
            ", outputPacket=" + outputPacket +
            '}';
    }
}
