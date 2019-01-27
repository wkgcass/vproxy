package net.cassite.vproxy.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class Utils {
    private Utils() {
    }

    public static int positive(byte b) {
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

    public static int maskInt(byte[] mask) {
        // run from end to start and check how many zeros
        int m = 0;
        for (int i = mask.length - 1; i >= 0; --i) {
            int cnt = zeros(mask[i]);
            if (cnt == 0)
                break;
            m += cnt;
        }
        return mask.length * 8 - m;
    }

    private static int zeros(byte b) {
        if ((b & /*-------*/0b1) == /*-------*/0b1) return 0;
        if ((b & /*------*/0b10) == /*------*/0b10) return 1;
        if ((b & /*-----*/0b100) == /*-----*/0b100) return 2;
        if ((b & /*----*/0b1000) == /*----*/0b1000) return 3;
        if ((b & /*---*/0b10000) == /*---*/0b10000) return 4;
        if ((b & /*--*/0b100000) == /*--*/0b100000) return 5;
        if ((b & /*-*/0b1000000) == /*-*/0b1000000) return 6;
        if ((b & /**/0b10000000) == /**/0b10000000) return 7;
        return 8;
    }

    public static boolean validNetwork(byte[] addr, byte[] mask) {
        if (addr.length < mask.length)
            return false;
        for (int i = 1; i <= mask.length; ++i) {
            byte a = addr[addr.length - i];
            byte m = mask[mask.length - i];
            if ((a & m) != a)
                return false;
        }
        return true;
    }

    public static boolean maskMatch(byte[] input, byte[] rule, byte[] mask) {
        int minLen = input.length;
        if (rule.length < minLen)
            minLen = rule.length;
        if (mask.length < minLen)
            minLen = mask.length;

        for (int i = minLen - 1; i >= 0; --i) {
            byte inputB = input[i];
            byte ruleB = rule[i];
            byte maskB = mask[i];

            if ((inputB & maskB) != ruleB)
                return false;
        }
        return true;
    }

    public static byte[] parseAddress(String address) {
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("unknown ip " + address);
        }
    }

    public static byte[] parseMask(int mask) {
        if (mask > 128) {
            throw new IllegalArgumentException("unknown mask " + mask);
        } else if (mask > 32) {
            // ipv6
            return getMask(new byte[16], mask);
        } else {
            // ipv4
            return getMask(new byte[4], mask);
        }
    }

    private static byte[] getMask(byte[] masks, int mask) {
        for (int i = 0; i < 4; ++i) {
            masks[i] = getByte(mask > 8 ? 8 : mask);
            mask -= 8;
        }
        return masks;
    }

    private static byte getByte(int ones) {
        switch (ones) {
            case 8:
                return (byte) 0b11111111;
            case 7:
                return (byte) 0b11111110;
            case 6:
                return (byte) 0b11111100;
            case 5:
                return (byte) 0b11111000;
            case 4:
                return (byte) 0b11110000;
            case 3:
                return (byte) 0b11100000;
            case 2:
                return (byte) 0b11000000;
            case 1:
                return (byte) 0b10000000;
            default:
                return 0;
        }
    }
}
