package io.vproxy.vswitch.stack.fd;

import io.vproxy.base.selector.wrap.WrappedSelector;
import io.vproxy.vpacket.conntrack.Conntrack;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.node.TcpStack;
import io.vproxy.vswitch.node.UdpOutput;

public class VSwitchFDContext {
    public final TcpStack tcpStack;
    public final UdpOutput udpOutput;
    public final VirtualNetwork network;
    public final Conntrack conntrack;
    public final WrappedSelector selector;

    public VSwitchFDContext(SwitchDelegate swCtx, VirtualNetwork network) {
        this.tcpStack = (TcpStack) swCtx.scheduler.graph.getNode("tcp-stack");
        this.udpOutput = (UdpOutput) swCtx.scheduler.graph.getNode("udp-output");
        this.network = network;
        this.conntrack = network.conntrack;
        this.selector = swCtx.getSelectorEventLoop().selector;
    }
}
