package vproxy.app.plugin.impl;

import vproxy.app.app.Application;
import vproxy.app.plugin.Plugin;
import vproxy.app.plugin.PluginInitParams;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.exception.NotFoundException;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.PacketFilterHelper;
import vproxy.vswitch.Switch;
import vproxy.vswitch.iface.Iface;
import vproxy.vswitch.plugin.FilterResult;
import vproxy.vswitch.plugin.IfaceWatcher;
import vproxy.vswitch.plugin.PacketFilter;
import vproxyx.pktfiltergen.IfaceHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class BasePacketFilter implements PacketFilter, IfaceWatcher, Plugin {
    private static final String UUID = "079c39ac-ce97-4820-a014-0ed16ad639db";
    private final Map<String, IfaceHolder> ifaces = new HashMap<>();
    private Switch handledSwitch;

    public BasePacketFilter() {
    }

    @Override
    public String id() {
        return getClass().getName() + ":" + UUID;
    }

    @Override
    public final void init(PluginInitParams params) throws Exception {
        // parse arguments
        String selectSwitch = null;
        for (String s : params.arguments) {
            if (s.startsWith("switch=")) {
                selectSwitch = s.substring("switch=".length()).trim();
                if (selectSwitch.isEmpty()) {
                    throw new Exception("invalid value for switch: should not be an empty string");
                }
            }
        }
        if (selectSwitch == null) {
            Logger.warn(LogType.ALERT, "switch={...} is not provided, the plugin will try to select any available switch");
        }

        var app = Application.get();
        if (app == null) {
            throw new Exception("Application is not initiated");
        }
        var swNames = app.switchHolder.names();
        for (var name : swNames) {
            Switch sw;
            try {
                sw = app.switchHolder.get(name);
            } catch (NotFoundException e) {
                Logger.warn(LogType.ALERT, "failed to retrieve switch " + name, e);
                continue;
            }
            if (selectSwitch != null && !selectSwitch.equals(sw.alias)) {
                continue;
            }
            if (handleSwitch(sw)) {
                this.handledSwitch = sw;
                break;
            }
        }
        if (handledSwitch == null) {
            throw new Exception("no switch to be handled");
        }
        Logger.alert("bind to switch " + handledSwitch.alias);
    }

    @Override
    public final void start() {
        handledSwitch.addIfaceWatcher(this);
    }

    @Override
    public final void stop() {
        handledSwitch.removeIfaceWatcher(this);
        for (var ifaceHolder : ifaces.values()) {
            var iface = ifaceHolder.iface;
            if (iface != null) {
                Logger.alert("removing ingress/egress filters from " + iface.name());
                iface.removeIngressFilter(this);
                iface.removeEgressFilter(this);
                ifaceHolder.iface = null;
            }
        }
    }

    @Override
    public final void destroy() {
        handledSwitch = null;
    }

    protected boolean handleSwitch(@SuppressWarnings("unused") Switch sw) {
        return true;
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
