#include "vproxy_checksum.h"

#include <stddef.h>

#define ETHER_TYPE_OFF           (12)
#define ETHER_TYPE_WITH_VLAN_OFF (16)
#define ETHER_PKT_NO_VLAN_OFF    (14)
#define ETHER_PKT_WITH_VLAN_OFF  (18)

#define ETHER_TYPE_8021Q (0x8100)
#define ETHER_TYPE_IPv4  (0x0800)
#define ETHER_TYPE_IPv6  (0x86dd)

#define IP4_IHL_OFF   (0)
#define IP4_LEN_OFF   (2)
#define IP4_PROTO_OFF (9)
#define IP4_CSUM_OFF (10)
#define IP4_SRC_OFF  (12)
#define IP4_DST_OFF  (16)

#define IP4_HDR_MIN_LEN (20)

#define IP6_PAYLOAD_LEN_OFF (4)
#define IP6_NEXT_HDR_OFF    (6)
#define IP6_SRC_OFF         (8)
#define IP6_DST_OFF        (24)

#define IP6_HDR_MIN_LEN (40)

#define IP_PROTOCOL_ICMP (1)
#define IP_PROTOCOL_ICMPv6 (58)
#define IP_PROTOCOL_TCP (6)
#define IP_PROTOCOL_UDP (17)

#define ICMP_CSUM_OFF (2)
#define TCP_CSUM_OFF (16)
#define UDP_CSUM_OFF  (6)

#ifdef DEBUG
#define VP_CSUM_DEBUG 1
#endif

#ifdef VP_CSUM_DEBUG
#include <stdio.h>
#endif

inline int vp_pkt_ether_type(char* raw) {
    return ((raw[ETHER_TYPE_OFF] & 0xff) << 8) | (raw[ETHER_TYPE_OFF + 1] & 0xff);
}

inline int vp_pkt_ether_type_with_vlan(char* raw) {
    return ((raw[ETHER_TYPE_WITH_VLAN_OFF] & 0xff) << 8) | (raw[ETHER_TYPE_WITH_VLAN_OFF + 1] & 0xff);
}

inline int vp_pkt_ipv4_hdr_len(char* raw) {
    return (raw[IP4_IHL_OFF] & 0xf) * 4;
}

inline int vp_pkt_ipv4_total_len(char* raw) {
    return ((raw[IP4_LEN_OFF] & 0xff) << 8) | (raw[IP4_LEN_OFF + 1] & 0xff);
}

inline int vp_pkt_ipv4_proto(char* raw) {
    return raw[IP4_PROTO_OFF] & 0xff;
}

inline int vp_pkt_ipv6_payload_len(char* raw) {
    return ((raw[IP6_PAYLOAD_LEN_OFF] & 0xff) << 8) | (raw[IP6_PAYLOAD_LEN_OFF + 1] & 0xff);
}

inline int vp_pkt_ipv6_next_hdr(char* raw) {
    return raw[IP6_NEXT_HDR_OFF] & 0xff;
}

inline int vp_pkt_ipv6_require_next_header(int next_header) {
    int n = next_header;
    return n == 0 || n == 60 || n == 43 || n == 44 || n == 51 || n == 50 || n == 135 || n == 139 || n == 140 || n == 253 || n == 254;
}

char* vp_pkt_ipv6_skip_to_last_hdr(char* raw, int len, char* proto) {
    int xh = vp_pkt_ipv6_next_hdr(raw);
    char* xh_buf = raw + IP6_HDR_MIN_LEN;
    int ex_off = IP6_HDR_MIN_LEN;
    while (vp_pkt_ipv6_require_next_header(xh)) {
        if (ex_off + 8 > len) {
            #ifdef VP_CSUM_DEBUG
            printf("pkt too short for ip6 opt\n");
            #endif
            return NULL;
        }
        xh = xh_buf[0] & 0xff;
        int len = xh_buf[1] & 0xff;
        xh_buf += 8 + len;
        ex_off += 8 + len;
    }
    if (ex_off > len) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for ip6 after parsing ip6 opt\n");
        #endif
        return NULL;
    }
    *proto = (char) xh;
    return xh_buf;
}

int vp_pkt_icmp4_csum(char* raw, int len, int flags) {
    if (!(flags & VP_CSUM_UP)) {
        return 0;
    }

    if (len < ICMP_CSUM_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for icmp4\n");
        #endif
        return 1;
    }
    raw[ICMP_CSUM_OFF] = 0;
    raw[ICMP_CSUM_OFF + 1] = 0;
    int csum = vp_csum_plain_calc(raw, len);
    raw[ICMP_CSUM_OFF] = (csum >> 8) & 0xff;
    raw[ICMP_CSUM_OFF + 1] = csum & 0xff;
    return 0;
}

int vp_pkt_tcp4_csum(char* ip, char* raw, int len, int flags) {
    if (!(flags & VP_CSUM_UP)) {
        return 0;
    }

    if (len < TCP_CSUM_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for tcp4\n");
        #endif
        return 1;
    }
    raw[TCP_CSUM_OFF] = 0;
    raw[TCP_CSUM_OFF + 1] = 0;
    int csum = vp_csum_ipv4_pseudo_calc(ip + IP4_SRC_OFF, ip + IP4_DST_OFF, IP_PROTOCOL_TCP, raw, len);
    raw[TCP_CSUM_OFF] = (csum >> 8) & 0xff;
    raw[TCP_CSUM_OFF + 1] = csum & 0xff;
    return 0;
}

int vp_pkt_udp4_csum(char* ip, char* raw, int len, int flags) {
    if (!(flags & VP_CSUM_UP)) {
        return 0;
    }

    if (len < UDP_CSUM_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for udp4\n");
        #endif
        return 1;
    }
    raw[UDP_CSUM_OFF] = 0;
    raw[UDP_CSUM_OFF + 1] = 0;
    int csum = vp_csum_ipv4_pseudo_calc(ip + IP4_SRC_OFF, ip + IP4_DST_OFF, IP_PROTOCOL_UDP, raw, len);
    if (csum == 0) {
        csum = 0xffff;
    }
    raw[UDP_CSUM_OFF] = (csum >> 8) & 0xff;
    raw[UDP_CSUM_OFF + 1] = csum & 0xff;
    return 0;
}

int vp_pkt_ipv4_csum(char* raw, int len, int flags) {
    if (len < IP4_HDR_MIN_LEN) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for ipv4\n");
        #endif
        return 1;
    }
    int hdr_len = vp_pkt_ipv4_hdr_len(raw);
    if (hdr_len < IP4_HDR_MIN_LEN) {
        #ifdef VP_CSUM_DEBUG
        printf("ipv4 hdr len < IP4_HDR_MIN_LEN\n");
        #endif
        return 1;
    }
    int total_len = vp_pkt_ipv4_total_len(raw);
    if (hdr_len > total_len) {
        #ifdef VP_CSUM_DEBUG
        printf("ipv4 hdr len > total len\n");
        #endif
        return 1;
    }
    if (total_len > len) {
        #ifdef VP_CSUM_DEBUG
        printf("ipv4 total len > pkt len\n");
        #endif
        return 1;
    }

    if (flags & VP_CSUM_IP) {
        raw[IP4_CSUM_OFF] = 0;
        raw[IP4_CSUM_OFF + 1] = 0;
        int csum = vp_csum_plain_calc(raw, hdr_len);
        raw[IP4_CSUM_OFF] = (csum >> 8) & 0xff;
        raw[IP4_CSUM_OFF + 1] = csum & 0xff;
    }

    char* upper = raw + hdr_len;
    int upper_len = total_len - hdr_len;
    int proto = vp_pkt_ipv4_proto(raw);
    if (proto == IP_PROTOCOL_ICMP) {
        return vp_pkt_icmp4_csum(upper, upper_len, flags);
    } else if (proto == IP_PROTOCOL_ICMPv6) {
        #ifdef VP_CSUM_DEBUG
        printf("ipv4 pkt but got icmp6\n");
        #endif
        return 1;
    } else if (proto == IP_PROTOCOL_TCP) {
        return vp_pkt_tcp4_csum(raw, upper, upper_len, flags);
    } else if (proto == IP_PROTOCOL_UDP) {
        return vp_pkt_udp4_csum(raw, upper, upper_len, flags);
    } else {
        #ifdef VP_CSUM_DEBUG
        printf("unhandled ip proto\n");
        #endif
        return 1;
    }
}

int vp_pkt_icmp6_csum(char* ip, char* raw, int len, int flags) {
    if (!(flags & VP_CSUM_UP)) {
        return 0;
    }

    if (len < ICMP_CSUM_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for icmp6\n");
        #endif
        return 1;
    }
    raw[ICMP_CSUM_OFF] = 0;
    raw[ICMP_CSUM_OFF + 1] = 0;
    int csum = vp_csum_ipv6_pseudo_calc(ip + IP6_SRC_OFF, ip + IP6_DST_OFF, IP_PROTOCOL_ICMPv6, raw, len);
    raw[ICMP_CSUM_OFF] = (csum >> 8) & 0xff;
    raw[ICMP_CSUM_OFF + 1] = csum & 0xff;
    return 0;
}

int vp_pkt_tcp6_csum(char* ip, char* raw, int len, int flags) {
    if (!(flags & VP_CSUM_UP)) {
        return 0;
    }

    if (len < TCP_CSUM_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for tcp6\n");
        #endif
        return 1;
    }
    raw[TCP_CSUM_OFF] = 0;
    raw[TCP_CSUM_OFF + 1] = 0;
    int csum = vp_csum_ipv6_pseudo_calc(ip + IP6_SRC_OFF, ip + IP6_DST_OFF, IP_PROTOCOL_TCP, raw, len);
    raw[TCP_CSUM_OFF] = (csum >> 8) & 0xff;
    raw[TCP_CSUM_OFF + 1] = csum & 0xff;
    return 0;
}

int vp_pkt_udp6_csum(char* ip, char* raw, int len, int flags) {
    if (!(flags & VP_CSUM_UP)) {
        return 0;
    }

    if (len < UDP_CSUM_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for udp6\n");
        #endif
        return 1;
    }
    raw[UDP_CSUM_OFF] = 0;
    raw[UDP_CSUM_OFF + 1] = 0;
    int csum = vp_csum_ipv6_pseudo_calc(ip + IP6_SRC_OFF, ip + IP6_DST_OFF, IP_PROTOCOL_UDP, raw, len);
    if (csum == 0) {
        csum = 0xffff;
    }
    raw[UDP_CSUM_OFF] = (csum >> 8) & 0xff;
    raw[UDP_CSUM_OFF + 1] = csum & 0xff;
    return 0;
}

int vp_pkt_ipv6_csum(char* raw, int len, int flags) {
    if (len < IP6_HDR_MIN_LEN) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for ipv6\n");
        #endif
        return 1;
    }
    int payload_len = vp_pkt_ipv6_payload_len(raw);
    if (IP6_HDR_MIN_LEN + payload_len > len) {
        #ifdef VP_CSUM_DEBUG
        printf("pkt too short for ipv6 payload\n");
        #endif
        return 1;
    }
    int xh = vp_pkt_ipv6_next_hdr(raw);
    char* upper = raw + IP6_HDR_MIN_LEN;
    if (vp_pkt_ipv6_require_next_header(xh)) {
        char proto;
        upper = vp_pkt_ipv6_skip_to_last_hdr(raw, len, &proto);
        if (upper == NULL) {
            return 1;
        }
        xh = proto & 0xff;
    }
    int upper_len = payload_len - (upper - raw - IP6_HDR_MIN_LEN);
    if (xh == IP_PROTOCOL_ICMP) {
        return vp_pkt_icmp4_csum(upper, upper_len, flags);
    } else if (xh == IP_PROTOCOL_ICMPv6) {
        return vp_pkt_icmp6_csum(raw, upper, upper_len, flags);
    } else if (xh == IP_PROTOCOL_TCP) {
        return vp_pkt_tcp6_csum(raw, upper, upper_len, flags);
    } else if (xh == IP_PROTOCOL_UDP) {
        return vp_pkt_udp6_csum(raw, upper, upper_len, flags);
    } else {
        #ifdef VP_CSUM_DEBUG
        printf("unhandled ip6 proto\n");
        #endif
        return 1;
    }
}

int vp_pkt_ether_csum(char* raw, int len, int flags) {
    if (flags == VP_CSUM_NO) {
        #ifdef VP_CSUM_DEBUG
        printf("no need to calculate\n");
        #endif
        return 1;
    }
    if (len < ETHER_TYPE_OFF + 2) {
        #ifdef VP_CSUM_DEBUG
        printf("ether pkt too short to get ether_type\n");
        #endif
        return 1;
    }
    int ether_type = vp_pkt_ether_type(raw);
    char* ippkt = raw + ETHER_PKT_NO_VLAN_OFF;
    int ip_len = len - ETHER_PKT_NO_VLAN_OFF;
    if (ether_type == ETHER_TYPE_8021Q) {
        if (len < ETHER_TYPE_WITH_VLAN_OFF + 2) {
            #ifdef VP_CSUM_DEBUG
            printf("ether pkt too short to get 802.1q type\n");
            #endif
            return 1;
        }
        ether_type = vp_pkt_ether_type_with_vlan(raw);
        ippkt = raw + ETHER_PKT_WITH_VLAN_OFF;
        ip_len = len - ETHER_PKT_WITH_VLAN_OFF;
    }
    if (ether_type != ETHER_TYPE_IPv4 && ether_type != ETHER_TYPE_IPv6) {
        #ifdef VP_CSUM_DEBUG
        printf("not ipv4 nor ipv6, skip the packet\n");
        #endif
        return 1;
    }
    if (ether_type == ETHER_TYPE_IPv4) {
        return vp_pkt_ipv4_csum(ippkt, ip_len, flags);
    } else {
        return vp_pkt_ipv6_csum(ippkt, ip_len, flags);
    }
}
