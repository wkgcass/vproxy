package vproxy.vswitch.stack.fd;

import vproxy.base.selector.wrap.WrappedSelector;
import vproxy.vpacket.conntrack.Conntrack;
import vproxy.vswitch.Switch;
import vproxy.vswitch.Table;
import vproxy.vswitch.stack.L4;

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
