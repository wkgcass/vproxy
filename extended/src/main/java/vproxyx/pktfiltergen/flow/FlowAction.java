package vproxyx.pktfiltergen.flow;

import vproxy.base.util.Consts;
import vproxy.vfd.IP;
import vproxy.vfd.IPv6;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.*;

public class FlowAction {
    public boolean normal;
    public boolean drop;
    public int table;

    public MacAddress mod_dl_dst;
    public MacAddress mod_dl_src;
    public IP mod_nw_src;
    public IP mod_nw_dst;
    public int mod_tp_src;
    public int mod_tp_dst;

    public String output;

    public String toStatementString(Flows.GenContext ctx) {
        if (normal) {
            return "return FilterResult.PASS";
        } else if (drop) {
            return "return FilterResult.DROP";
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
        return normal || drop || table != 0;
    }

    public boolean allowTerminating() {
        return isTerminator() || output != null;
    }
}
