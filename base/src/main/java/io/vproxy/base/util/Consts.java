package io.vproxy.base.util;

import io.vproxy.vfd.IP;

import java.util.Set;

public class Consts {
    public static final String USER_PADDING = "+";

    public static final int AF_INET = 2;
    public static final int AF_INET6 = 10;

    public static final int VPROXY_SWITCH_MAGIC = 0x8776;
    public static final int VPROXY_SWITCH_TYPE_PING = 2;
    public static final int VPROXY_SWITCH_TYPE_VXLAN = 1;
    public static final int ETHER_TYPE_8021Q = 0x8100;
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

    public static final byte TCP_OPTION_END = 0;
    public static final byte TCP_OPTION_NOP = 1;
    public static final byte TCP_OPTION_MSS = 2;
    public static final byte TCP_OPTION_WINDOW_SCALE = 3;

    public static final byte TCP_FLAGS_URG = 0b100000;
    public static final byte TCP_FLAGS_ACK = 0b010000;
    public static final byte TCP_FLAGS_PSH = 0b001000;
    public static final byte TCP_FLAGS_RST = 0b000100;
    public static final byte TCP_FLAGS_SYN = 0b000010;
    public static final byte TCP_FLAGS_FIN = 0b000001;

    // https://tools.ietf.org/html/rfc2131
    public static final byte DHCP_OP_BOOTREQUEST = 1;
    public static final byte DHCP_OP_BOOTREPLY = 2;
    public static final byte DHCP_HTYPE_ETHERNET = 1;
    public static final int DHCP_MAGIC_COOKIE = 0x63825363;

    // https://tools.ietf.org/html/rfc2132
    public static final byte DHCP_OPT_TYPE_END = (byte) 0xff;
    public static final byte DHCP_OPT_TYPE_PAD = 0;
    public static final byte DHCP_OPT_TYPE_DNS = 6;
    public static final byte DHCP_OPT_TYPE_MSG_TYPE = 53;
    public static final byte DHCP_OPT_TYPE_PARAM_REQ_LIST = 55;

    public static final byte DHCP_MSG_TYPE_DHCPDISCOVER = 1;
    public static final byte DHCP_MSG_TYPE_DHCPOFFER = 2;
    public static final byte DHCP_MSG_TYPE_DHCPREQUEST = 3;
    public static final byte DHCP_MSG_TYPE_DHCPDECLINE = 4;
    public static final byte DHCP_MSG_TYPE_DHCPACK = 5;
    public static final byte DHCP_MSG_TYPE_DHCPNAK = 6;
    public static final byte DHCP_MSG_TYPE_DHCPRELEASE = 7;
    public static final byte DHCP_MSG_TYPE_DHCPINFORM = 8;

    public static final int XDP_HEADROOM_DRIVER_RESERVED = 256;

    public static final int I_DETECTED_A_POSSIBLE_LOOP =
        0b00001000_00000000_00000000;
    public static final int I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN =
        0b00000100_00000000_00000000;
    public static final int I_AM_FROM_SWITCH =
        0b00100000_00000000_00000000;
}
