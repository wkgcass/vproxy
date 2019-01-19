package net.cassite.vproxy.util;

import java.util.Arrays;

public class Utils {
    private Utils() {
    }

    private static String ipv6Str(byte[] ip) {
        return "[" + Integer.toHexString(ip[0] << 8 + ip[1])
            + ":" + Integer.toHexString(ip[2] << 8 + ip[3])
            + ":" + Integer.toHexString(ip[4] << 8 + ip[5])
            + ":" + Integer.toHexString(ip[6] << 8 + ip[7])
            + ":" + Integer.toHexString(ip[8] << 8 + ip[9])
            + ":" + Integer.toHexString(ip[10] << 8 + ip[11])
            + ":" + Integer.toHexString(ip[12] << 8 + ip[13])
            + ":" + Integer.toHexString(ip[14] << 8 + ip[15])
            + "]";
    }

    private static String ipv4Str(byte[] ip) {
        return ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3];
    }

    public static String ipStr(byte[] ip) {
        if (ip.length == 16) {
            return ipv6Str(ip);
        } else if (ip.length == 4) {
            return ipv4Str(ip);
        } else {
            throw new IllegalArgumentException("unknown ip " + Arrays.toString(ip));
        }
    }
}
