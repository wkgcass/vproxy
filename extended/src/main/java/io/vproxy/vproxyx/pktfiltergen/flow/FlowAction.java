package io.vproxy.vproxyx.pktfiltergen.flow;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.net.IPPortPool;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;

public class FlowAction {
    public boolean normal;
    public boolean drop;
    public boolean tx;
    public boolean l3tx;
    public int table;

    public MacAddress mod_dl_dst;
    public MacAddress mod_dl_src;
    public IP mod_nw_src;
    public IP mod_nw_dst;
    public int mod_tp_src;
    public int mod_tp_dst;

    public String output;

    public long limit_bps;
    public long limit_pps;

    public boolean nat;
    public IPPort dnat;
    public IPPortPool snat;
    public Tuple<IPPortPool, IPPort> fnat;

    public String run;
    public String invoke;

    public String toStatementString(Flows.GenContext ctx) {
        if (normal) {
            return "return FilterResult.PASS";
        } else if (drop) {
            return "return FilterResult.DROP";
        } else if (tx) {
            return "return FilterResult.TX";
        } else if (l3tx) {
            return "return FilterResult.L3_TX";
        } else if (table != 0) {
            return "return table" + table + "(helper, pkb)";
        } else if (mod_dl_dst != null) {
            return "pkb.pkt.setDst(" + ctx.fieldName(mod_dl_dst) + ")";
        } else if (mod_dl_src != null) {
            return "pkb.pkt.setSrc(" + ctx.fieldName(mod_dl_src) + ")";
        } else if (mod_nw_src != null) {
            return castIP(ctx, mod_nw_src) + ".setSrc(" + ctx.fieldName(mod_nw_src) + ")";
        } else if (mod_nw_dst != null) {
            return castIP(ctx, mod_nw_dst) + ".setDst(" + ctx.fieldName(mod_nw_dst) + ")";
        } else if (mod_tp_src != 0) {
            return castTransport(ctx) + ".setSrcPort(" + mod_tp_src + ")";
        } else if (mod_tp_dst != 0) {
            return castTransport(ctx) + ".setDstPort(" + mod_tp_dst + ")";
        } else if (output != null) {
            return "helper.sendPacket(pkb, ifaces[" + ctx.ifaceIndex(output) + "].iface)";
        } else if (limit_bps != 0) {
            return "if (!helper.ratelimitByBitsPerSecond(pkb, ratelimiters[" + ctx.newBPSRateLimiter(limit_bps) + "])) {\n" +
                "\treturn FilterResult.DROP;\n" +
                "}";
        } else if (limit_pps != 0) {
            return "if (!helper.ratelimitByPacketsPerSecond(pkb, ratelimiters[" + ctx.newPPSRateLimiter(limit_pps) + "])) {\n" +
                "\treturn FilterResult.DROP;\n" +
                "}";
        } else if (nat) {
            return "if (!helper.executeNat(pkb)) {\n" +
                "\treturn FilterResult.DROP;\n" +
                "}";
        } else if (dnat != null) {
            return "if (!helper.executeDNat(pkb, " + ctx.fieldName(dnat) + ")) {\n" +
                "\treturn FilterResult.DROP;\n" +
                "}";
        } else if (snat != null) {
            return "if (!helper.executeSNat(pkb, " + ctx.fieldName(snat) + ")) {\n" +
                "\treturn FilterResult.DROP;\n" +
                "}";
        } else if (fnat != null) {
            return "if (!helper.executeFNat(pkb, " + ctx.fieldName(fnat._1) + ", " + ctx.fieldName(fnat._2) + ")) {\n" +
                "\treturn FilterResult.DROP;\n" +
                "}";
        } else if (run != null) {
            ctx.registerRunnableMethods(run);
            return "run_" + run + "(helper, pkb)";
        } else if (invoke != null) {
            ctx.registerInvocationMethods(invoke);
            return "return invoke_" + invoke + "(helper, pkb)";
        } else {
            throw new IllegalStateException("cannot generate statement for " + this);
        }
    }

    private String castIP(Flows.GenContext ctx, IP ip) {
        if (ip instanceof IPv6) {
            ctx.ensureImport(Ipv6Packet.class);
            return "((Ipv6Packet) pkb.pkt.getPacket())";
        } else {
            ctx.ensureImport(Ipv4Packet.class);
            return "((Ipv4Packet) pkb.pkt.getPacket())";
        }
    }

    private String castTransport(Flows.GenContext ctx) {
        ctx.ensureImport(AbstractIpPacket.class);
        if (ctx.getCurrentFlow().matcher.nw_proto == Consts.IP_PROTOCOL_UDP) {
            ctx.ensureImport(UdpPacket.class);
            return "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket())";
        } else {
            ctx.ensureImport(TcpPacket.class);
            return "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket())";
        }
    }

    @Override
    public String toString() {
        if (normal) {
            return "normal";
        } else if (drop) {
            return "drop";
        } else if (tx) {
            return "tx";
        } else if (l3tx) {
            return "l3tx";
        } else if (table != 0) {
            return "goto_table:" + table;
        } else if (mod_dl_dst != null) {
            return "mod_dl_dst:" + mod_dl_dst;
        } else if (mod_dl_src != null) {
            return "mod_dl_src:" + mod_dl_src;
        } else if (mod_nw_src != null) {
            return "mod_nw_src:" + ipFormat(mod_nw_src);
        } else if (mod_nw_dst != null) {
            return "mod_nw_dst:" + ipFormat(mod_nw_dst);
        } else if (mod_tp_src != 0) {
            return "mod_tp_src:" + mod_tp_src;
        } else if (mod_tp_dst != 0) {
            return "mod_tp_dst:" + mod_tp_dst;
        } else if (output != null) {
            return "output:" + output;
        } else if (limit_bps != 0) {
            return "limit_bps:" + limit_bps;
        } else if (limit_pps != 0) {
            return "limit_pps:" + limit_pps;
        } else if (nat) {
            return "nat";
        } else if (dnat != null) {
            return "dnat:" + dnat.formatToIPPortString();
        } else if (snat != null) {
            return "snat:" + snat.serialize();
        } else if (fnat != null) {
            return "fnat:" + fnat._1.serialize() + "^" + fnat._2.formatToIPPortString();
        } else if (run != null) {
            return "run:" + run;
        } else if (invoke != null) {
            return "invoke:" + invoke;
        } else {
            return "???";
        }
    }

    private String ipFormat(IP ip) {
        String str = ip.formatToIPString();
        if (str.startsWith("[")) {
            return str.substring(1, str.length() - 1);
        } else {
            return str;
        }
    }

    public boolean isTerminator() {
        return normal || drop || tx || l3tx || table != 0 || invoke != null;
    }

    public boolean allowTerminating() {
        return isTerminator() || output != null;
    }
}
