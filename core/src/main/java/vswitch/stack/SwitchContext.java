package vswitch.stack;

import vpacket.VXLanPacket;
import vswitch.Table;
import vswitch.iface.Iface;

import java.util.Collection;

public class SwitchContext {
    public SwitchContext(SendingPacket sendPacketFunc,
                         GetIfaces getIfacesFunc,
                         GetTable getTableFunc) {
        this.sendPacketFunc = sendPacketFunc;
        this.getIfacesFunc = getIfacesFunc;
        this.getTableFunc = getTableFunc;
    }

    public interface SendingPacket {
        void send(VXLanPacket packet, Iface iface);
    }

    private final SendingPacket sendPacketFunc;

    public void sendPacket(VXLanPacket packet, Iface toIface) {
        sendPacketFunc.send(packet, toIface);
    }

    public interface GetIfaces {
        Collection<Iface> getIfaces();
    }

    private final GetIfaces getIfacesFunc;

    public Collection<Iface> getIfaces() {
        return getIfacesFunc.getIfaces();
    }

    public interface GetTable {
        Table getTable(int vni);
    }

    private final GetTable getTableFunc;

    public Table getTable(int vni) {
        return getTableFunc.getTable(vni);
    }
}
