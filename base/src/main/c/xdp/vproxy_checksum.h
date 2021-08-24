#ifndef VPROXY_CHECKSUM_H
#define VPROXY_CHECKSUM_H

#define VP_CSUM_NO (0)
#define VP_CSUM_IP (1)
#define VP_CSUM_UP (2)
#define VP_CSUM_ALL (VP_CSUM_UP | VP_CSUM_IP)

inline int vp_csum_calc0(int sum, char* data, int len) {
    for (int i = 0; i < len / 2; ++i) {
        sum += ((data[2 * i] & 0xff) << 8) | (data[2 * i + 1] & 0xff);
        while (sum > 0xffff) {
            sum = (sum & 0xffff) + 1;
        }
    }
    if (len % 2 != 0) {
        sum += ((data[len - 1] & 0xff) << 8);
        while (sum > 0xffff) {
            sum = (sum & 0xffff) + 1;
        }
    }
    return sum;
}

inline int vp_csum_plain_calc(char* data, int len) {
    int n = vp_csum_calc0(0, data, len);
    return 0xffff - n;
}

inline int vp_csum_ipv4_pseudo_calc(char* src, char* dst, char proto, char* data, int datalen) {
    int sum = vp_csum_calc0(0, src, 4);
    sum = vp_csum_calc0(sum, dst, 4);
    char foo[2];
    foo[0] = 0;
    foo[1] = proto;
    sum = vp_csum_calc0(sum, foo, 2);
    foo[0] = (datalen >> 8) & 0xff;
    foo[1] = datalen & 0xff;
    sum = vp_csum_calc0(sum, foo, 2);
    sum = vp_csum_calc0(sum, data, datalen);
    return 0xffff - sum;
}

inline int vp_csum_ipv6_pseudo_calc(char* src, char* dst, char proto, char* data, int datalen) {
    int sum = vp_csum_calc0(0, src, 16);
    sum = vp_csum_calc0(sum, dst, 16);
    char foo[4];
    foo[0] = (datalen >> 24) & 0xff;
    foo[1] = (datalen >> 16) & 0xff;
    foo[2] = (datalen >> 8) & 0xff;
    foo[3] = datalen & 0xff;
    sum = vp_csum_calc0(sum, foo, 4);
    foo[0] = 0;
    foo[1] = 0;
    foo[2] = 0;
    foo[3] = proto;
    sum = vp_csum_calc0(sum, foo, 4);
    sum = vp_csum_calc0(sum, data, datalen);
    return 0xffff - sum;
}

int vp_pkt_ether_csum(char* raw, int len, int flags);

#endif
