package vswitch.util;

import vfd.IP;
import vproxy.util.ByteArray;

import java.util.Set;

public class Consts {
    public static final String USER_PADDING = "+";

    public static final int VPROXY_SWITCH_MAGIC = 0x8776;
    public static final int VPROXY_SWITCH_TYPE_PING = 2;
    public static final int VPROXY_SWITCH_TYPE_VXLAN = 1;
    public static final int ETHER_TYPE_ARP = 0x0806;
    public static final int ETHER_TYPE_IPv4 = 0x0800;
    public static final int ETHER_TYPE_IPv6 = 0x86dd;
    public static final int ARP_PROTOCOL_TYPE_IP = 0x0800;
    public static final int ARP_HARDWARE_TYPE_ETHER = 1;
    public static final int ARP_PROTOCOL_OPCODE_REQ = 1;
    public static final int ARP_PROTOCOL_OPCODE_RESP = 2;
    public static final int IP_PROTOCOL_ICMP = 1;
    public static final int IP_PROTOCOL_ICMPv6 = 58;
    public static final int IP_PROTOCOL_TCP = 6;
    public static final int IP_PROTOCOL_UDP = 17;
    public static final int IP_PROTOCOL_SCTP = 132;
    public static final int IPv6_NEXT_HEADER_NO_NEXT_HEADER = 59;
    public static final Set<Integer> IPv6_needs_next_header = Set.of(0, 60, 43, 44, 51, 50, 135, 139, 140, 253, 254);
    public static final int ICMP_PROTOCOL_TYPE_ECHO_REQ = 8;
    public static final int ICMPv6_PROTOCOL_TYPE_ECHO_REQ = 128;
    public static final int ICMP_PROTOCOL_TYPE_ECHO_RESP = 0;
    public static final int ICMPv6_PROTOCOL_TYPE_ECHO_RESP = 129;
    public static final int ICMP_PROTOCOL_TYPE_TIME_EXCEEDED = 11;
    public static final int ICMPv6_PROTOCOL_TYPE_TIME_EXCEEDED = 3;
    public static final int ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation = 135;
    public static final int ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement = 136;
    public static final int ICMPv6_OPTION_TYPE_Source_Link_Layer_Address = 1;
    public static final int ICMPv6_OPTION_TYPE_Target_Link_Layer_Address = 2;
    public static final int ICMP_PROTOCOL_TYPE_DEST_UNREACHABLE = 3;
    public static final int ICMP_PROTOCOL_CODE_PORT_UNREACHABLE = 3;
    public static final int ICMPv6_PROTOCOL_TYPE_DEST_UNREACHABLE = 1;
    public static final int ICMPv6_PROTOCOL_CODE_PORT_UNREACHABLE = 4;
    public static final int IPv6_NEXT_HEADER_HOP_BY_HOP_OPTIONS = 0;
    public static final int IPv6_NEXT_HEADER_ROUTING = 43;

    public static final ByteArray IPv6_Solicitation_Node_Multicast_Address = ByteArray.from(IP.parseIpString("ff02::1:ff00:0"));

    public static final byte TCP_FLAGS_PSH = 0b1000;
    public static final byte TCP_FLAGS_RST = 0b0100;
    public static final byte TCP_FLAGS_SYN = 0b0010;
    public static final byte TCP_FLAGS_FIN = 0b0001;

    public static final int I_DETECTED_A_POSSIBLE_LOOP =
        0b00001000_00000000_00000000;
    public static final int I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN =
        0b00000100_00000000_00000000;
    public static final int I_AM_FROM_SWITCH =
        0b00100000_00000000_00000000;
}
