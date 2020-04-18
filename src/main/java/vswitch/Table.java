package vswitch;

import vproxy.selector.SelectorEventLoop;

public class Table {
    public final int vni;
    public final ForwardingTable forwardingTable;
    public final ArpTable arpTable;
    private int forwardingTableTimeout;
    private int arpTableTimeout;

    public Table(int vni, SelectorEventLoop loop,
                 int forwardingTableTimeout, int arpTableTimeout) {
        this.vni = vni;
        this.forwardingTableTimeout = forwardingTableTimeout;
        this.arpTableTimeout = arpTableTimeout;

        forwardingTable = new ForwardingTable(loop, forwardingTableTimeout);
        arpTable = new ArpTable(loop, arpTableTimeout);
    }

    public void setForwardingTableTimeout(int forwardingTableTimeout) {
        this.forwardingTableTimeout = forwardingTableTimeout;
        forwardingTable.setTimeout(forwardingTableTimeout);
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        this.arpTableTimeout = arpTableTimeout;
        arpTable.setTimeout(arpTableTimeout);
    }

    public void clear() {
        forwardingTable.clear();
        arpTable.clear();
    }
}
