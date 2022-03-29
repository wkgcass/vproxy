package io.vproxy.vproxyx.pktfiltergen.flow;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.bitwise.BitwiseIntMatcher;
import io.vproxy.base.util.bitwise.BitwiseMatcher;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;

public class FlowParser {
    private final String input;
    private final Flow flow = new Flow();
    private int state = 0;
    // 0: parsing matcher
    // 1: parsing actions
    // 2: end

    public FlowParser(String input) {
        this.input = input;
    }

    public Flow parse() throws Exception {
        if (state == 2) {
            return flow;
        }
        String[] split = input.split(",");
        for (String s : split) {
            if (state == 0) {
                parseMatcher(s);
            } else {
                FlowAction action = new FlowAction();
                parseAction(action, s);
                flow.actions.add(action);
            }
        }
        finalValidate();
        state = 2;
        return flow;
    }

    private void parseMatcher(String s) throws Exception {
        switch (s) {
            case "arp":
                setEtherType(Consts.ETHER_TYPE_ARP);
                return;
            case "ip":
                setEtherType(Consts.ETHER_TYPE_IPv4);
                return;
            case "ipv6":
                setEtherType(Consts.ETHER_TYPE_IPv6);
                return;
            case "icmp":
                setEtherType(Consts.ETHER_TYPE_IPv4);
                setIPProto(Consts.IP_PROTOCOL_ICMP);
                return;
            case "icmp6":
                setEtherType(Consts.ETHER_TYPE_IPv6);
                setIPProto(Consts.IP_PROTOCOL_ICMPv6);
                return;
            case "tcp":
                setEtherType(Consts.ETHER_TYPE_IPv4);
                setIPProto(Consts.IP_PROTOCOL_TCP);
                return;
            case "tcp6":
                setEtherType(Consts.ETHER_TYPE_IPv6);
                setIPProto(Consts.IP_PROTOCOL_TCP);
                return;
            case "udp":
                setEtherType(Consts.ETHER_TYPE_IPv4);
                setIPProto(Consts.IP_PROTOCOL_UDP);
                return;
            case "udp6":
                setEtherType(Consts.ETHER_TYPE_IPv6);
                setIPProto(Consts.IP_PROTOCOL_UDP);
                return;
        }
        if (!s.contains("=")) {
            throw new Exception("unknown matcher: " + s);
        }
        String key;
        String value;
        {
            int firstIndex = s.indexOf("=");
            key = s.substring(0, firstIndex);
            value = s.substring(firstIndex + 1);
        }
        switch (key) {
            case "table":
                isNotSet(flow.table, "table");
                flow.table = parseNonNegativeInt(value);
                return;
            case "priority":
                isNotSet(flow.priority, "priority");
                flow.priority = parseNonNegativeInt(value);
                return;
            case "in_port":
                isNotSet(flow.matcher.in_port, "in_port");
                flow.matcher.in_port = value;
                return;
            case "dl_dst":
                isNotSet(flow.matcher.dl_dst, "dl_dst");
                flow.matcher.dl_dst = macMatcher(value);
                return;
            case "dl_src":
                isNotSet(flow.matcher.dl_src, "dl_src");
                flow.matcher.dl_src = macMatcher(value);
                return;
            case "dl_type":
                setEtherType(parseNonNegativeInt(value));
                return;
            case "arp_op":
                isNotSet(flow.matcher.arp_op, "arp_op");
                setEtherType(Consts.ETHER_TYPE_ARP);
                flow.matcher.arp_op = parseNonNegativeInt(value);
                return;
            case "arp_spa":
                isNotSet(flow.matcher.arp_spa, "arp_spa");
                setEtherType(Consts.ETHER_TYPE_ARP);
                flow.matcher.arp_spa = ipMatcher(value, false);
                return;
            case "arp_tpa":
                isNotSet(flow.matcher.arp_tpa, "arp_tpa");
                setEtherType(Consts.ETHER_TYPE_ARP);
                flow.matcher.arp_tpa = ipMatcher(value, false);
                return;
            case "arp_sha":
                isNotSet(flow.matcher.arp_sha, "arp_sha");
                setEtherType(Consts.ETHER_TYPE_ARP);
                flow.matcher.arp_sha = macMatcher(value);
                return;
            case "arp_tha":
                isNotSet(flow.matcher.arp_tha, "arp_tha");
                setEtherType(Consts.ETHER_TYPE_ARP);
                flow.matcher.arp_tha = macMatcher(value);
                return;
            case "nw_src":
                dlTypeIsIPOrNotSet();
                isNotSet(flow.matcher.nw_src, "nw_src");
                flow.matcher.nw_src = ipMatcher(value);
                return;
            case "nw_dst":
                dlTypeIsIPOrNotSet();
                isNotSet(flow.matcher.nw_dst, "nw_dst");
                flow.matcher.nw_dst = ipMatcher(value);
                return;
            case "nw_proto":
                dlTypeIsIPOrNotSet();
                if (flow.matcher.dl_type == 0) {
                    setEtherType(Consts.ETHER_TYPE_IPv4);
                }
                setIPProto(parseNonNegativeInt(value));
                return;
            case "tp_src":
                nwProtoTP();
                isNotSet(flow.matcher.tp_src, "tp_src");
                flow.matcher.tp_src = portMatcher(value);
                return;
            case "tp_dst":
                nwProtoTP();
                isNotSet(flow.matcher.tp_dst, "tp_dst");
                flow.matcher.tp_dst = portMatcher(value);
                return;
            case "vni":
                flow.matcher.vni = parsePositiveInt(value);
                return;
            case "predicate":
                assertValidMethodName(value);
                flow.matcher.predicate = value;
                return;

            case "action":
            case "actions":
                state = 1;
                FlowAction action = new FlowAction();
                parseAction(action, value);
                flow.actions.add(action);
                return;
        }
        throw new Exception("unknown matcher: " + s);
    }

    private void parseAction(FlowAction action, String s) throws Exception {
        switch (s) {
            case "pass":
            case "PASS":
            case "normal":
            case "NORMAL":
            case "ACCEPT":
            case "accept":
                action.normal = true;
                return;
            case "drop":
            case "DROP":
                action.drop = true;
                return;
            case "tx":
            case "TX":
                action.tx = true;
                return;
            case "l3tx":
            case "L3TX":
            case "L3_TX":
                action.l3tx = true;
                return;
        }
        if (!s.contains(":")) {
            throw new Exception("unknown action: " + s);
        }
        String key;
        String value;
        {
            int first = s.indexOf(":");
            key = s.substring(0, first);
            value = s.substring(first + 1);
        }
        switch (key) {
            case "goto_table":
                action.table = parsePositiveInt(value);
                return;
            case "mod_dl_dst":
                action.mod_dl_dst = parseMac(value);
                return;
            case "mod_dl_src":
                action.mod_dl_src = parseMac(value);
                return;
            case "mod_nw_src":
                dlTypeIsIP();
                action.mod_nw_src = parseIP(value);
                return;
            case "mod_nw_dst":
                dlTypeIsIP();
                action.mod_nw_dst = parseIP(value);
                return;
            case "mod_tp_src":
                nwProtoTP();
                action.mod_tp_src = parsePort(value);
                return;
            case "mod_tp_dst":
                nwProtoTP();
                action.mod_tp_dst = parsePort(value);
                return;
            case "output":
                action.output = value;
                return;
            case "limit_bps":
                action.limit_bps = parsePositiveLong(value);
                return;
            case "limit_pps":
                action.limit_pps = parsePositiveLong(value);
                return;
            case "run":
                assertValidMethodName(value);
                action.run = value;
                return;
            case "invoke":
                assertValidMethodName(value);
                action.invoke = value;
                return;
        }
        throw new Exception("unknown action: " + s);
    }

    private long parseNonNegativeLong(String value) throws Exception {
        long n;
        try {
            if (value.startsWith("0x")) {
                n = Long.parseLong(value.substring("0x".length()), 16);
            } else {
                n = Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            throw new Exception(value + " is not a valid integer");
        }
        if (n < 0) {
            throw new Exception(value + " is negative");
        }
        return n;
    }

    private int parseNonNegativeInt(String value) throws Exception {
        long nl = parseNonNegativeLong(value);
        int n = (int) nl;
        if ((long) n != nl) {
            throw new Exception(value + " too large");
        }
        return n;
    }

    private int parsePositiveInt(String value) throws Exception {
        int n = parseNonNegativeInt(value);
        if (n == 0) {
            throw new Exception("zero not allowed");
        }
        return n;
    }

    private long parsePositiveLong(String value) throws Exception {
        long n = parseNonNegativeLong(value);
        if (n == 0) {
            throw new Exception("zero not allowed");
        }
        return n;
    }

    private void assertValidMethodName(String value) throws Exception {
        char[] chars = value.toCharArray();
        if (chars.length == 0) {
            throw new Exception("no method name provided");
        }
        if ('0' <= chars[0] && chars[0] <= '9') {
            throw new Exception(chars[0] + "(" + ((int) chars[0]) + ")" + " is not allowed to be the first char in method name: " + value);
        }
        for (char c : chars) {
            if (('a' <= c && c <= 'z')
                || ('A' <= c && c <= 'Z')
                || ('0' <= c && c <= '9')
                || c == '$'
                || c == '_') {
                continue;
            }
            throw new Exception(c + "(" + ((int) c) + ")" + " is not allowed in a method name: " + value);
        }
    }

    private void dlTypeIsIPOrNotSet() throws Exception {
        if (flow.matcher.dl_type == 0) return;
        dlTypeIsIP();
    }

    private void dlTypeIsIP() throws Exception {
        if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv4) return;
        if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv6) return;
        throw new Exception("dl_type is neither ip nor ipv6: " + Utils.toHexString(flow.matcher.dl_type));
    }

    private void nwProtoTP() throws Exception {
        if (flow.matcher.nw_proto == Consts.IP_PROTOCOL_TCP) return;
        if (flow.matcher.nw_proto == Consts.IP_PROTOCOL_UDP) return;
        throw new Exception("dl_type is neither tcp nor udp: " + Utils.toHexString(flow.matcher.nw_proto));
    }

    private MacAddress parseMac(String value) throws Exception {
        try {
            return new MacAddress(value);
        } catch (IllegalArgumentException e) {
            throw new Exception(value + " is not a valid mac address");
        }
    }

    private BitwiseMatcher macMatcher(String value) throws Exception {
        if (value.contains("/")) {
            return macMaskMatcher(value);
        }
        MacAddress mac;
        try {
            mac = new MacAddress(value);
        } catch (IllegalArgumentException ignore) {
            return exactMatcher(value, 6);
        }
        return BitwiseMatcher.from(mac.bytes);
    }

    private BitwiseMatcher macMaskMatcher(String value) throws Exception {
        String[] split = value.split("/");
        if (split.length != 2) {
            throw new Exception(value + " is not a valid bitwise matcher: contains multiple `/`");
        }
        MacAddress matcher;
        MacAddress mask;
        try {
            matcher = new MacAddress(split[0]);
            mask = new MacAddress(split[1]);
        } catch (IllegalArgumentException e) {
            return matcher(value, 6);
        }
        return BitwiseMatcher.from(matcher.bytes, mask.bytes);
    }

    private IP parseIP(String value) throws Exception {
        return parseIP(value, true);
    }

    private IP parseIP(String value, boolean assumeEtherType) throws Exception {
        IP ip;
        try {
            ip = IP.from(value);
        } catch (IllegalArgumentException e) {
            throw new Exception(value + " is not a valid ip address");
        }
        checkAndSetIPEtherType(value, assumeEtherType, ip);
        return ip;
    }

    private BitwiseMatcher ipMatcher(String value) throws Exception {
        return ipMatcher(value, true);
    }

    private BitwiseMatcher ipMatcher(String value, boolean assumeEtherType) throws Exception {
        if (value.contains("/")) {
            Network net;
            try {
                net = Network.from(value);
            } catch (IllegalArgumentException e) {
                if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv6) {
                    return matcher(value, 16);
                } else {
                    if (assumeEtherType) {
                        setEtherType(Consts.ETHER_TYPE_IPv4);
                    }
                    return matcher(value, 4);
                }
            }
            if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv6) {
                if (!(net.getIp() instanceof IPv6)) {
                    throw new Exception(value + " is not ipv6");
                }
            } else if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv4) {
                if (!(net.getIp() instanceof IPv4)) {
                    throw new Exception(value + " is not ipv4");
                }
            } else {
                if (assumeEtherType) {
                    if (net.getIp() instanceof IPv6) {
                        setEtherType(Consts.ETHER_TYPE_IPv6);
                    } else {
                        setEtherType(Consts.ETHER_TYPE_IPv4);
                    }
                }
            }
            return BitwiseMatcher.from(ByteArray.from(net.getRawIpBytes()), ByteArray.from(net.getRawMaskBytes()), true);
        }
        IP ip;
        try {
            ip = IP.from(value);
        } catch (IllegalArgumentException ignore) {
            if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv6) {
                return exactMatcher(value, 16);
            } else {
                if (assumeEtherType) {
                    setEtherType(Consts.ETHER_TYPE_IPv4);
                }
                return exactMatcher(value, 4);
            }
        }
        checkAndSetIPEtherType(value, assumeEtherType, ip);
        return BitwiseMatcher.from(ip.bytes);
    }

    private void checkAndSetIPEtherType(String value, boolean assumeEtherType, IP ip) throws Exception {
        if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv6) {
            if (!(ip instanceof IPv6)) {
                throw new Exception(value + " is not ipv6");
            }
        } else if (flow.matcher.dl_type == Consts.ETHER_TYPE_IPv4) {
            if (!(ip instanceof IPv4)) {
                throw new Exception(value + " is not ipv4");
            }
        } else {
            if (assumeEtherType) {
                if (ip instanceof IPv6) {
                    setEtherType(Consts.ETHER_TYPE_IPv6);
                } else {
                    setEtherType(Consts.ETHER_TYPE_IPv4);
                }
            }
        }
    }

    private int parsePort(String value) throws Exception {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new Exception(value + " is not a valid port");
        }
        if (port < 1 || port > 65535) {
            throw new Exception(value + " is not a valid port: out of range");
        }
        return port;
    }

    private BitwiseIntMatcher portMatcher(String value) throws Exception {
        if (value.contains("/")) {
            BitwiseMatcher m = matcher(value, 2);
            return BitwiseIntMatcher.from(
                (((m.getMatcher().get(0) << 8) & 0xff00) | (m.getMatcher().get(1) & 0xff)),
                (((m.getMask().get(0) << 8) & 0xff00) | (m.getMask().get(1) & 0xff))
            );
        }
        int port = parsePort(value);
        return BitwiseIntMatcher.from(port);
    }

    private BitwiseMatcher matcher(String value, int bytesCount) throws Exception {
        if (!value.contains("/")) {
            return exactMatcher(value, bytesCount);
        }
        String[] split = value.split("/");
        if (split.length != 2) {
            throw new Exception(value + " is not a valid bitwise matcher: contains multiple `/`");
        }
        var matcher = parseIntoByteArray(split[0]);
        var mask = parseIntoByteArray(split[1]);
        if (matcher.length() != bytesCount) {
            throw new Exception("invalid bit sequence length, expecting " + (bytesCount * 8) + ": " + value);
        }
        try {
            return BitwiseMatcher.from(matcher, mask);
        } catch (IllegalArgumentException e) {
            throw new Exception(value + " is not a valid bitwise matcher: " + e.getMessage());
        }
    }

    private BitwiseMatcher exactMatcher(String value, int bytesCount) throws Exception {
        ByteArray array = parseIntoByteArray(value);
        if (array.length() != bytesCount) {
            throw new Exception("invalid bit sequence length, expecting " + (bytesCount * 8) + ": " + value);
        }
        return BitwiseMatcher.from(array);
    }

    private ByteArray parseIntoByteArray(String value) throws Exception {
        if (value.startsWith("0x")) {
            value = value.substring("0x".length());
            try {
                return ByteArray.fromHexString(value);
            } catch (IllegalArgumentException e) {
                throw new Exception(value + " is not a valid bit sequence");
            }
        } else {
            if (value.startsWith("0b")) {
                value = value.substring("0b".length());
            }
            try {
                return ByteArray.fromBinString(value);
            } catch (IllegalArgumentException e) {
                throw new Exception(value + " is not a valid bit sequence");
            }
        }
    }

    private void isNotSet(Object o, String name) throws Exception {
        if (o != null) {
            throw new Exception(name + " is already set: " + o);
        }
    }

    private void isNotSet(int o, String name) throws Exception {
        if (o != 0) {
            throw new Exception(name + " is already set: " + o);
        }
    }

    private void setEtherType(int dlType) throws Exception {
        if (flow.matcher.dl_type != 0) {
            if (flow.matcher.dl_type != dlType) {
                throw new Exception("dl_type is already set or assumed: " + Utils.toHexString(flow.matcher.dl_type));
            }
        }
        flow.matcher.dl_type = dlType;
    }

    private void setIPProto(int nwProto) throws Exception {
        if (flow.matcher.nw_proto != 0) {
            if (flow.matcher.nw_proto != nwProto) {
                throw new Exception("nw_proto is already set or assumed: " + Utils.toHexString(flow.matcher.nw_proto));
            }
        }
        flow.matcher.nw_proto = nwProto;
    }

    private void finalValidate() throws Exception {
        if (flow.actions.isEmpty()) {
            throw new Exception("missing actions");
        }
        for (int i = 0; i < flow.actions.size(); ++i) {
            FlowAction action = flow.actions.get(i);
            if (action.isTerminator()) {
                if (i + 1 != flow.actions.size()) {
                    throw new Exception(action + " must be the last action");
                }
            }
            if (action.table != 0 && action.table <= flow.table) {
                throw new Exception("cannot goto_table to previous tables: flow.table=" + flow.table + ", " + action);
            }
        }
        var last = flow.actions.get(flow.actions.size() - 1);
        if (!last.allowTerminating()) {
            throw new Exception("actions cannot end with " + last);
        }
    }
}
