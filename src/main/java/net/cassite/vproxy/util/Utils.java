package net.cassite.vproxy.util;

import java.util.Arrays;

public class Utils {
    private Utils() {
    }

    private static int positive(byte b) {
        if (b < 0) return 256 + b;
        return b;
    }

    private static String ipv6Str(byte[] ip) {
        return "[" + Integer.toHexString(positive(ip[0]) << 8 + positive(ip[1]))
            + ":" + Integer.toHexString(positive(ip[2]) << 8 + positive(ip[3]))
            + ":" + Integer.toHexString(positive(ip[4]) << 8 + positive(ip[5]))
            + ":" + Integer.toHexString(positive(ip[6]) << 8 + positive(ip[7]))
            + ":" + Integer.toHexString(positive(ip[8]) << 8 + positive(ip[9]))
            + ":" + Integer.toHexString(positive(ip[10]) << 8 + positive(ip[11]))
            + ":" + Integer.toHexString(positive(ip[12]) << 8 + positive(ip[13]))
            + ":" + Integer.toHexString(positive(ip[14]) << 8 + positive(ip[15]))
            + "]";
    }

    private static String ipv4Str(byte[] ip) {
        return positive(ip[0]) + "." + positive(ip[1]) + "." + positive(ip[2]) + "." + positive(ip[3]);
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

    public static String formatErr(Throwable err) {
        if (err.getMessage() != null && !err.getMessage().trim().isEmpty()) {
            return err.getMessage().trim();
        } else {
            return err.toString();
        }
    }
}
