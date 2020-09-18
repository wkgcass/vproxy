package vswitch.stack;

import vpacket.AbstractEthernetPacket;
import vpacket.VXLanPacket;
import vswitch.Table;
import vswitch.iface.Iface;

public class InputPacketL2Context {
    public final String handlingUUID;
    public final Iface inputIface;
    public final Table table;
    public final VXLanPacket inputVXLan;
    public final AbstractEthernetPacket inputPacket;

    public InputPacketL2Context(String handlingUUID,
                                Table table,
                                AbstractEthernetPacket inputPacket) {
        this(handlingUUID, null, table, null, inputPacket);
    }

    public InputPacketL2Context(String handlingUUID,
                                Iface iface,
                                Table table,
                                AbstractEthernetPacket inputPacket) {
        this(handlingUUID, iface, table, null, inputPacket);
    }

    public InputPacketL2Context(String handlingUUID,
                                Iface inputIface,
                                Table table,
                                VXLanPacket inputPacket) {
        this(handlingUUID, inputIface, table, inputPacket, inputPacket.getPacket());
    }

    public InputPacketL2Context(String handlingUUID,
                                Iface inputIface,
                                Table table,
                                VXLanPacket inputVXLan,
                                AbstractEthernetPacket inputPacket) {
        this.handlingUUID = handlingUUID;
        this.inputIface = inputIface;
        this.table = table;
        this.inputVXLan = inputVXLan;
        this.inputPacket = inputPacket;
    }

    public InputPacketL2Context(InputPacketL2Context ctx) {
        this(ctx.handlingUUID, ctx.inputIface, ctx.table, ctx.inputVXLan, ctx.inputPacket);
    }

    public void clearVXLanRawPacket() {
        if (inputVXLan != null) {
            inputVXLan.clearRawPacket();
        }
    }

    @Override
    public String toString() {
        return "InputPacketL2Context{" +
            "handlingUUID='" + handlingUUID + '\'' +
            ", inputIface=" + inputIface +
            ", table=" + table.vni +
            ", inputVXLan=" + inputVXLan +
            ", inputPacket=" + inputPacket +
            '}';
    }
}
