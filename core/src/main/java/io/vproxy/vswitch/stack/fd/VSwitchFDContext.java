package vproxy.vswitch.stack.fd;

import vproxy.base.selector.wrap.WrappedSelector;
import vproxy.vpacket.conntrack.Conntrack;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.VirtualNetwork;
import vproxy.vswitch.stack.L4;

public class VSwitchFDContext {
    public final L4 L4;
    public final VirtualNetwork network;
    public final Conntrack conntrack;
    public final WrappedSelector selector;

    public VSwitchFDContext(SwitchContext swCtx, VirtualNetwork network) {
        this.L4 = swCtx.netStack.get().L4;
        this.network = network;
        this.conntrack = network.conntrack;
        this.selector = swCtx.getSelectorEventLoop().selector;
    }
}
