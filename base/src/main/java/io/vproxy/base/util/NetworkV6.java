package io.vproxy.base.util;

import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;

public class NetworkV6 extends Network {
    protected final int a;
    protected final int b;
    protected final int c;
    protected final int d;

    protected NetworkV6(IPv6 ip, ByteArray mask) {
        super(ip, mask);
        a = ip.getIPv6Value0();
        b = ip.getIPv6Value1();
        c = ip.getIPv6Value2();
        d = ip.getIPv6Value3();
    }

    @Override
    public boolean contains(IP address) {
        int mask = getMask();
        if (address instanceof IPv4) {
            if (a == 0 && b == 0 && (c == 0xffff || c == 0)) {
                if (mask < 128 - 32) {
                    return true;
                }
                return (((IPv4) address).getIPv4Value() & Utils.maskNumberToInt(mask - (128 - 32))) == d;
            } else {
                return false;
            }
        } else if (address instanceof IPv6) {
            int m = ((IPv6) address).getIPv6Value0();
            int n = ((IPv6) address).getIPv6Value1();
            int o = ((IPv6) address).getIPv6Value2();
            int p = ((IPv6) address).getIPv6Value3();

            if (mask >= 128) {
                return a == m && b == n && c == o && d == p;
            } else if (mask > 96) {
                return a == m && b == n && c == o && ((p & Utils.maskNumberToInt(mask - 96)) == d);
            } else if (mask > 64) {
                return a == m && b == n && ((o & Utils.maskNumberToInt(mask - 64)) == c);
            } else if (mask > 32) {
                return a == m && ((n & Utils.maskNumberToInt(mask - 32)) == b);
            } else {
                return (m & Utils.maskNumberToInt(mask)) == a;
            }
        } else {
            return false;
        }
    }
}
