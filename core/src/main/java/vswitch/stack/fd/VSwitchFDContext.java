package vswitch.stack.fd;

import vproxybase.selector.wrap.WrappedSelector;
import vswitch.Switch;
import vswitch.Table;
import vpacket.conntrack.Conntrack;
import vswitch.stack.L4;

public class VSwitchFDContext {
    public final L4 L4;
    public final Table table;
    public final Conntrack conntrack;
    public final WrappedSelector selector;

    public VSwitchFDContext(Switch sw,
                            Table table,
                            WrappedSelector selector) {
        L4 = sw.netStack.L2.L3.L4;
        this.table = table;
        this.conntrack = table.conntrack;
        this.selector = selector;
    }
}
