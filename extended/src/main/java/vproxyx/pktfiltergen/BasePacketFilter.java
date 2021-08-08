package vproxyx.pktfiltergen;

import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.PacketFilterHelper;
import vproxy.vswitch.iface.Iface;
import vproxy.vswitch.plugin.FilterResult;
import vproxy.vswitch.plugin.IfaceWatcher;
import vproxy.vswitch.plugin.PacketFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class BasePacketFilter implements PacketFilter, IfaceWatcher {
    private final Map<String, IfaceHolder> ifaces = new HashMap<>();

    public BasePacketFilter() {
    }

    @SuppressWarnings("unused")
    protected final void registerIfaceHolder(IfaceHolder holder) {
        ifaces.put(holder.name, holder);
    }

    @Override
    public final void ifaceAdded(Iface iface) {
        IfaceHolder holder = ifaces.get(iface.name());
        if (holder == null) {
            ifaces.put(iface.name(), new IfaceHolder(iface.name(), iface));
        } else {
            holder.iface = iface;
        }
        iface.addIngressFilter(this);
        iface.addEgressFilter(this);
    }

    @Override
    public final void ifaceRemoved(Iface iface) {
        IfaceHolder holder = ifaces.get(iface.name());
        if (holder != null) {
            holder.iface = null;
        }
    }

    protected final FilterResult execute(
        PacketFilterHelper helper,
        PacketBuffer pkb,
        BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> exec) {
        return exec.apply(helper, pkb);
    }

    @SuppressWarnings("unused")
    protected FilterResult handleIngress(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.PASS;
    }

    @SuppressWarnings("unused")
    protected FilterResult handleEgress(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.PASS;
    }

    @Override
    public final FilterResult handle(PacketFilterHelper helper, PacketBuffer pkb) {
        if (pkb.devout == null) {
            return handleIngress(helper, pkb);
        } else {
            return handleEgress(helper, pkb);
        }
    }
}
