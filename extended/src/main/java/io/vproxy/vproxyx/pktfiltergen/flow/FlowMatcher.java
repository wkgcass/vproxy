package io.vproxy.vproxyx.pktfiltergen.flow;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.bitwise.BitwiseIntMatcher;
import io.vproxy.base.util.bitwise.BitwiseMatcher;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;

public class FlowMatcher {
    // dev
    public String in_port;

    // ethernet
    public BitwiseMatcher dl_dst;
    public BitwiseMatcher dl_src;
    public int dl_type;

    // arp
    public int arp_op;
    public BitwiseMatcher arp_spa;
    public BitwiseMatcher arp_tpa;
    public BitwiseMatcher arp_sha;
    public BitwiseMatcher arp_tha;

    // ip
    public BitwiseMatcher nw_src;
    public BitwiseMatcher nw_dst;
    public int nw_proto;

    // tcp or udp
    public BitwiseIntMatcher tp_src;
    public BitwiseIntMatcher tp_dst;

    // vni
    public int vni;

    // customized
    public String predicate;

    public String toIfConditionString(Flows.GenContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (in_port != null) {
            appendAnd(sb).append("pkb.devin == ifaces[").append(ctx.ifaceIndex(in_port)).append("].iface");
        }
        if (dl_dst != null) {
            appendAnd(sb).append(ctx.fieldName(dl_dst)).append(".match(pkb.pkt.getDst())");
        }
        if (dl_src != null) {
            appendAnd(sb).append(ctx.fieldName(dl_src)).append(".match(pkb.pkt.getSrc())");
        }
        if (dl_type != 0) {
            appendAnd(sb).append("pkb.pkt.getType() == ").append(dl_type);
            if (dl_type == Consts.ETHER_TYPE_IPv4) {
                appendAnd(sb).append("pkb.pkt.getPacket() instanceof Ipv4Packet");
                ctx.ensureImport(Ipv4Packet.class);
            } else if (dl_type == Consts.ETHER_TYPE_IPv6) {
                appendAnd(sb).append("pkb.pkt.getPacket() instanceof Ipv6Packet");
                ctx.ensureImport(Ipv6Packet.class);
            }
        }
        if (arp_op != 0) {
            appendAnd(sb).append(castArp(ctx)).append(".getOpcode() == ").append(arp_op);
        }
        if (arp_spa != null) {
            appendAnd(sb).append(ctx.fieldName(arp_spa)).append(".match(").append(castArp(ctx)).append(".getSenderIp()").append(")");
        }
        if (arp_tpa != null) {
            appendAnd(sb).append(ctx.fieldName(arp_tpa)).append(".match(").append(castArp(ctx)).append(".getTargetIp()").append(")");
        }
        if (arp_sha != null) {
            appendAnd(sb).append(ctx.fieldName(arp_sha)).append(".match(").append(castArp(ctx)).append(".getSenderMac()").append(")");
        }
        if (arp_tha != null) {
            appendAnd(sb).append(ctx.fieldName(arp_tha)).append(".match(").append(castArp(ctx)).append(".getTargetMac()").append(")");
        }
        if (nw_src != null) {
            appendAnd(sb).append(ctx.fieldName(nw_src)).append(".match(").append(castIp(ctx)).append(".getSrc()").append(")");
        }
        if (nw_dst != null) {
            appendAnd(sb).append(ctx.fieldName(nw_dst)).append(".match(").append(castIp(ctx)).append(".getDst()").append(")");
        }
        if (nw_proto != 0) {
            appendAnd(sb).append(castIp(ctx)).append(".getProtocol()").append(" == ").append(nw_proto);
        }
        if (tp_src != null) {
            appendAnd(sb).append(ctx.fieldName(tp_src)).append(".match(").append(castTransport(ctx)).append(".getSrcPort()").append(")");
        }
        if (tp_dst != null) {
            appendAnd(sb).append(ctx.fieldName(tp_dst)).append(".match(").append(castTransport(ctx)).append(".getDstPort()").append(")");
        }
        if (vni != 0) {
            appendAnd(sb).append("pkb.network != null && pkb.network.vni == ").append(vni);
        }
        if (predicate != null) {
            ctx.registerPredicateMethod(predicate);
            appendAnd(sb).append("predicate_").append(predicate).append("(helper, pkb)");
        }
        return sb.toString();
    }

    private String castArp(Flows.GenContext ctx) {
        ctx.ensureImport(ArpPacket.class);
        return "((ArpPacket) pkb.pkt.getPacket())";
    }

    private String castIp(Flows.GenContext ctx) {
        ctx.ensureImport(AbstractIpPacket.class);
        return "((AbstractIpPacket) pkb.pkt.getPacket())";
    }

    private String castTransport(Flows.GenContext ctx) {
        ctx.ensureImport(AbstractIpPacket.class);
        if (nw_proto == Consts.IP_PROTOCOL_UDP) {
            ctx.ensureImport(UdpPacket.class);
            return "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket())";
        } else {
            ctx.ensureImport(TcpPacket.class);
            return "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket())";
        }
    }

    private StringBuilder appendAnd(StringBuilder sb) {
        if (sb.length() != 0) {
            sb.append(" && ");
        }
        return sb;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (in_port != null) {
            appendSplit(sb).append("in_port=").append(in_port);
        }
        if (dl_dst != null) {
            appendSplit(sb).append("dl_dst=").append(formatMac(dl_dst));
        }
        if (dl_src != null) {
            appendSplit(sb).append("dl_src=").append(formatMac(dl_src));
        }
        if (dl_type != 0) {
            appendSplit(sb).append(formatEtherType());
        }
        if (arp_op != 0) {
            appendSplit(sb).append("arp_op=").append(Utils.toHexString(arp_op));
        }
        if (arp_spa != null) {
            appendSplit(sb).append("arp_spa=").append(formatIp(arp_spa));
        }
        if (arp_tpa != null) {
            appendSplit(sb).append("arp_tpa=").append(formatIp(arp_tpa));
        }
        if (arp_sha != null) {
            appendSplit(sb).append("arp_sha=").append(formatMac(arp_sha));
        }
        if (arp_tha != null) {
            appendSplit(sb).append("arp_tha=").append(formatMac(arp_tha));
        }
        if (nw_src != null) {
            appendSplit(sb).append("nw_src=").append(formatIp(nw_src));
        }
        if (nw_dst != null) {
            appendSplit(sb).append("nw_dst=").append(formatIp(nw_dst));
        }
        if (nw_proto != 0) {
            appendSplit(sb).append(formatIPProto());
        }
        if (tp_src != null) {
            appendSplit(sb).append("tp_src=").append(formatPort(tp_src));
        }
        if (tp_dst != null) {
            appendSplit(sb).append("tp_dst=").append(formatPort(tp_dst));
        }
        if (vni != 0) {
            appendSplit(sb).append("vni=").append(vni);
        }
        if (predicate != null) {
            appendSplit(sb).append("predicate=").append(predicate);
        }
        return sb.toString();
    }

    private StringBuilder appendSplit(StringBuilder sb) {
        if (sb.length() != 0) {
            sb.append(",");
        }
        return sb;
    }

    private String formatMac(BitwiseMatcher mac) {
        if (mac.maskAll()) {
            return new MacAddress(mac.getMatcher()).toString();
        } else {
            return new MacAddress(mac.getMatcher()) + "/" + new MacAddress(mac.getMask());
        }
    }

    private String formatIp(BitwiseMatcher ip) {
        if (ip.maskAll()) {
            String ipstr = IP.from(ip.getMatcher().toJavaArray()).formatToIPString();
            if (ipstr.startsWith("[")) {
                ipstr = ipstr.substring(1, ipstr.length() - 1);
            }
            return ipstr;
        } else {
            ByteArray mask = ip.getMask();
            boolean prefixAllOne = true;
            int i;
            for (i = 0; i < mask.length(); ++i) {
                byte b = mask.get(i);
                if (b == (byte) 0xff) {
                    continue;
                }
                if (b == 0) {
                    ++i;
                    break;
                }
                boolean foundZero = false;
                for (int j = 0; j < 8; ++j) {
                    int n = (b >> (7 - j)) & 0x1;
                    if (foundZero) {
                        if (n == 1) {
                            prefixAllOne = false;
                            break;
                        }
                    } else {
                        if (n == 0) {
                            foundZero = true;
                        }
                    }
                }
                if (foundZero) {
                    ++i;
                    break;
                }
            }
            if (prefixAllOne) {
                for (; i < mask.length(); ++i) {
                    if (mask.get(i) != 0) {
                        prefixAllOne = false;
                        break;
                    }
                }
            }
            if (prefixAllOne) {
                String netstr = new Network(ip.getMatcher().toJavaArray(), ip.getMask().toJavaArray()).toString();
                if (netstr.startsWith("[")) {
                    netstr = netstr.substring(1);
                    netstr = netstr.replace("]", "");
                }
                return netstr;
            } else {
                return ip.toString();
            }
        }
    }

    private String formatPort(BitwiseIntMatcher port) {
        if (port.maskAll()) {
            return "" + port.getMatcher();
        } else {
            return port.toString();
        }
    }

    private String formatEtherType() {
        switch (dl_type) {
            case Consts.ETHER_TYPE_IPv4:
                return "ip";
            case Consts.ETHER_TYPE_IPv6:
                return "ipv6";
            case Consts.ETHER_TYPE_ARP:
                return "arp";
            default:
                return "dl_type=" + Utils.toHexString(dl_type);
        }
    }

    private String formatIPProto() {
        switch (nw_proto) {
            case Consts.IP_PROTOCOL_ICMP:
                return "icmp";
            case Consts.IP_PROTOCOL_ICMPv6:
                return "icmp6";
            case Consts.IP_PROTOCOL_TCP:
                return dl_type == Consts.ETHER_TYPE_IPv6 ? "tcp6" : "tcp";
            case Consts.IP_PROTOCOL_UDP:
                return dl_type == Consts.ETHER_TYPE_IPv6 ? "udp6" : "udp";
            default:
                return "nw_proto=" + Utils.toHexString(nw_proto);
        }
    }
}
