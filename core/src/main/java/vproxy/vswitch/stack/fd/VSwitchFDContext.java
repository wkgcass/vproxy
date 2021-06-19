package vproxy.vswitch.stack.fd;

import vproxy.base.selector.wrap.WrappedSelector;
import vproxy.vpacket.conntrack.Conntrack;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.Table;
import vproxy.vswitch.stack.L4;

public class VSwitchFDContext {
    public final L4 L4;
    public final Table table;
    public final Conntrack conntrack;
    public final WrappedSelector selector;

    public VSwitchFDContext(SwitchContext swCtx,
                            Table table,
                            WrappedSelector selector) {
        this.L4 = swCtx.netStack.get().L4;
        this.table = table;
        this.conntrack = table.conntrack;
        this.selector = selector;
    }
}
