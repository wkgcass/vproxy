package io.vproxy.app.plugin.impl;

import io.vproxy.app.app.Application;
import io.vproxy.app.plugin.Plugin;
import io.vproxy.app.plugin.PluginInitParams;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vproxyx.pktfiltergen.IfaceHolder;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.plugin.FilterResult;
import io.vproxy.vswitch.plugin.IfaceWatcher;
import io.vproxy.vswitch.plugin.PacketFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class BasePacketFilter implements PacketFilter, IfaceWatcher, Plugin {
    private static final String UUID = "079c39ac-ce97-4820-a014-0ed16ad639db";
    private final Map<String, IfaceHolder> ifaces = new HashMap<>();
    private Switch handledSwitch;

    private boolean enableIngressCache = false;
    private Supplier<MicroFlow> ingressMicroFlowConstructor;
    private MicroFlow ingressMicroFlow = null;

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
        int cacheSize = 1048576;
        for (String s : params.arguments) {
            if (s.startsWith("switch=")) {
                selectSwitch = s.substring("switch=".length()).trim();
                if (selectSwitch.isEmpty()) {
                    throw new Exception("invalid value for switch: should not be an empty string");
                }
            } else if (s.startsWith("cacheSize=")) {
                var value = s.substring("cacheSize=".length());
                if (!Utils.isPositiveInteger(value)) {
                    throw new Exception("invalid value for cacheSize: must be a positive integer");
                }
                cacheSize = Integer.parseInt(value);
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
        final int finalCacheSize = cacheSize;
        ingressMicroFlowConstructor = () -> new MicroFlow(finalCacheSize);
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

    protected boolean handleIface(@SuppressWarnings("unused") Iface iface) {
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
        if (handleIface(iface)) {
            iface.addIngressFilter(this);
            iface.addEgressFilter(this);
        }
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
        if (pkb.devout == null) {
            recordIngressCache(pkb, exec);
        }
        return exec.apply(helper, pkb);
    }

    private void recordIngressCache(PacketBuffer pkb, BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> exec) {
        if (!enableIngressCache) {
            return;
        }
        var tuple = pkb.getFullTuple();
        if (tuple != null) {
            ingressMicroFlow.record(tuple, exec);
        }
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
            var res = handleIngressByCache(helper, pkb);
            if (res != null) {
                return res;
            }
            return handleIngress(helper, pkb);
        } else {
            return handleEgress(helper, pkb);
        }
    }

    private FilterResult handleIngressByCache(PacketFilterHelper helper, PacketBuffer pkb) {
        if (!enableIngressCache) {
            return null;
        }
        var tuple = pkb.getFullTuple();
        if (tuple == null) return null;
        var exec = ingressMicroFlow.lookup(tuple);
        if (exec == null) return null;
        assert Logger.lowLevelDebug("execute packet filter from flow cache: " + tuple);
        return exec.apply(helper, pkb);
    }

    protected void setEnableIngressCache(boolean enableIngressCache) {
        if (enableIngressCache) {
            if (ingressMicroFlow == null) {
                ingressMicroFlow = ingressMicroFlowConstructor.get();
            }
        }
        this.enableIngressCache = enableIngressCache;
    }

    protected void clearIngressCache() {
        if (enableIngressCache) {
            ingressMicroFlow = ingressMicroFlowConstructor.get();
        } else {
            ingressMicroFlow = null;
        }
    }

    @Override
    public String inspect() {
        StringBuilder sb = new StringBuilder();
        if (ingressMicroFlow != null) {
            sb.append("ingress.microflow{hit=").append(ingressMicroFlow.getHitCount())
                .append(",miss=").append(ingressMicroFlow.getMissCount())
                .append(",cache=").append(ingressMicroFlow.getCurrentCacheCount())
                .append("}");
        } else {
            sb.append("ingress.microflow{hit=0,miss=0,cache=0}");
        }
        return sb.toString();
    }
}
