package vproxy.vswitch.stack;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.vpacket.VXLanPacket;
import vproxy.vswitch.Table;
import vproxy.vswitch.iface.Iface;

import java.util.Collection;

public class SwitchContext {
    public SwitchContext(SendingPacket sendPacketFunc,
                         GetIfaces getIfacesFunc,
                         GetTable getTableFunc,
                         GetSelectorEventLoop getSelectorEventLoopFunc) {
        this.sendPacketFunc = sendPacketFunc;
        this.getIfacesFunc = getIfacesFunc;
        this.getTableFunc = getTableFunc;
        this.getSelectorEventLoopFunc = getSelectorEventLoopFunc;
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

    public interface GetSelectorEventLoop {
        SelectorEventLoop getSelectorEventLoop();
    }

    private final GetSelectorEventLoop getSelectorEventLoopFunc;

    public SelectorEventLoop getSelectorEventLoop() {
        return getSelectorEventLoopFunc.getSelectorEventLoop();
    }
}
