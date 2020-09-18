package vswitch.stack;

import vpacket.AbstractEthernetPacket;
import vswitch.Table;

public class OutputPacketL2Context {
    public final String handlingUUID;
    public final Table table;
    public final AbstractEthernetPacket outputPacket;

    public OutputPacketL2Context(String handlingUUID,
                                 Table table,
                                 AbstractEthernetPacket outputPacket) {
        this.handlingUUID = handlingUUID;
        this.table = table;
        this.outputPacket = outputPacket;
    }

    @Override
    public String toString() {
        return "OutputPacketL2Context{" +
            "handlingUUID='" + handlingUUID + '\'' +
            ", table=" + table.vni +
            ", outputPacket=" + outputPacket +
            '}';
    }
}
