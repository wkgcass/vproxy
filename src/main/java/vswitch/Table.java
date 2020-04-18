package vswitch;

import vproxy.selector.SelectorEventLoop;

public class Table {
    public final int vni;
    public final MacTable macTable;
    public final ArpTable arpTable;

    public Table(int vni, SelectorEventLoop loop,
                 int macTableTimeout, int arpTableTimeout) {
        this.vni = vni;

        macTable = new MacTable(loop, macTableTimeout);
        arpTable = new ArpTable(loop, arpTableTimeout);
    }

    public void setMacTableTimeout(int macTableTimeout) {
        macTable.setTimeout(macTableTimeout);
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        arpTable.setTimeout(arpTableTimeout);
    }

    public void clear() {
        macTable.clear();
        arpTable.clear();
    }
}
