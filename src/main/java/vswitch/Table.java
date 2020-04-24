package vswitch;

import vproxy.selector.SelectorEventLoop;

public class Table {
    public final int vni;
    public final MacTable macTable;
    public final ArpTable arpTable;
    public final SyntheticIpHolder ips;

    public Table(int vni, SelectorEventLoop loop,
                 int macTableTimeout, int arpTableTimeout) {
        this.vni = vni;

        macTable = new MacTable(loop, macTableTimeout);
        arpTable = new ArpTable(loop, arpTableTimeout);
        ips = new SyntheticIpHolder();
    }

    public void setMacTableTimeout(int macTableTimeout) {
        macTable.setTimeout(macTableTimeout);
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        arpTable.setTimeout(arpTableTimeout);
    }

    public void clearCache() {
        macTable.clearCache();
        arpTable.clearCache();
    }

    public void setLoop(SelectorEventLoop loop) {
        macTable.setLoop(loop);
        arpTable.setLoop(loop);
    }
}
