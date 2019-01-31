package net.cassite.vproxy.util;

import net.cassite.vproxy.dns.Resolver;

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

    private static String addTo(@SuppressWarnings("SameParameterValue") int len, String s) {
        if (s.length() >= len)
            return s;
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < len; ++i) {
            sb.append("0");
        }
        sb.append(s);
        return sb.toString();
    }

    private static String ipv6Str(byte[] ip) {
        return "[" + addTo(4, Integer.toHexString(((ip[0] << 8) & 0xFFFF) | ip[1]))
            + ":" + addTo(4, Integer.toHexString(((ip[2] << 8) & 0xFFFF) | ip[3]))
            + ":" + addTo(4, Integer.toHexString(((ip[4] << 8) & 0xFFFF) | ip[5]))
            + ":" + addTo(4, Integer.toHexString(((ip[6] << 8) & 0xFFFF) | ip[7]))
            + ":" + addTo(4, Integer.toHexString(((ip[8] << 8) & 0xFFFF) | ip[9]))
            + ":" + addTo(4, Integer.toHexString(((ip[10] << 8) & 0xFFFF) | ip[11]))
            + ":" + addTo(4, Integer.toHexString(((ip[12] << 8) & 0xFFFF) | ip[13]))
            + ":" + addTo(4, Integer.toHexString(((ip[14] << 8) & 0xFFFF) | ip[15]))
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
            return false; // ipv4 and mask > 32, absolutely wrong
        // only check first few bytes in the loop
        for (int i = 0; i < mask.length; ++i) {
            byte a = addr[i];
            byte m = mask[i];
            if ((a & m) != a)
                return false;
        }
        // check whether last few bytes are all zero
        // because mask is zero
        for (int i = mask.length; i < addr.length; ++i) {
            byte a = addr[i];
            if (a != 0)
                return false;
        }
        return true;
    }

    public static boolean maskMatch(byte[] input, byte[] rule, byte[] mask) {
        // the mask and rule length might not match each other
        // see comments in parseMask()
        // and input length might not be the same as the rule
        // because we might apply an ipv4 rule to an ipv6 lb

        // let's consider all situations:
        // 1) input.length == rule.length > mask.length
        //    which means ipv6 input and ipv6 rule and mask <= 32
        //    so we check the first 4 bytes in the sequence
        // 2) input.length < rule.length > mask.length
        //    which means ipv4 input and ipv6 rule and mask <= 32
        //    in this case, all highest 32 bits of real mask are 0
        //    and the requester's ip address cannot be 0.0.0.0
        //    so returning `not matching` would be ok
        // 3) input.length < rule.length == mask.length
        //    which means ipv4 input and ipv6 rule and mask > 32
        //    the low bits are 0 for ipv4
        //    so if rule low bits [all 0] or [all 0][ffff], then check high bits
        //    otherwise directly returning `not matching` would be ok
        // 4) input.length > rule.length == mask.length
        //    which means ipv6 input and ipv4 rule
        //    so let's only compare the last 32 bits
        //    additionally:
        //    there might be deprecated `IPv4-Compatible IPv6 address` e.g.:
        //                                  127.0.0.1
        //    0000:0000:0000:0000:0000:0000:7f00:0001
        //    and there might be `IPv4-Mapped IPv6 address` e.g.:
        //                                  127.0.0.1
        //    0000:0000:0000:0000:0000:ffff:7f00:0001
        //    so let's then check whether the first few bits
        //    like this [all 0][ffff]
        // 5) input.length == rule.length == mask.length
        //    which means ipv4 input and ipv4 rule and mask <= 32
        //    or ipv6 input and ipv6 input and mask > 32
        //    just do normal check
        //    see implementation for detail

        if (input.length == rule.length && rule.length > mask.length) {
            // 1
            for (int i = 0; i < mask.length; ++i) {
                byte inputB = input[i];
                byte ruleB = rule[i];
                byte maskB = mask[i];
                if ((inputB & maskB) != ruleB)
                    return false;
            }
            return true;
        } else if (input.length < rule.length && rule.length > mask.length) {
            // 2
            return false;
        } else if (input.length < rule.length && rule.length == mask.length) {
            // 3
            // input =            [.......]
            //  rule = [..................]
            int lastLowIdx = rule.length - input.length - 1;
            int secondLastLowIdx = lastLowIdx - 1;
            // high part
            for (int i = 0; i < input.length; ++i) {
                byte inputB = input[i];
                byte ruleB = rule[i + rule.length - input.length];
                byte maskB = mask[i + rule.length - input.length];
                if ((inputB & maskB) != ruleB)
                    return false;
            }
            return lowBitsV6V4(rule, lastLowIdx, secondLastLowIdx);
        }
        // else:
        // for (4) and (5)

        int minLen = input.length;
        if (rule.length < minLen)
            minLen = rule.length;
        if (mask.length < minLen)
            minLen = mask.length;

        for (int i = 0; i < minLen; ++i) {
            byte inputB = input[input.length - i - 1];
            byte ruleB = rule[rule.length - i - 1];
            byte maskB = mask[mask.length - i - 1];

            if ((inputB & maskB) != ruleB)
                return false;
        }

        // then check for additional rules in (4)
        if (input.length > rule.length) {
            // input = [..................]
            //  rule =            [.......]
            int lastLowIdx = input.length - rule.length - 1;
            int secondLastLowIdx = lastLowIdx - 1;
            return lowBitsV6V4(input, lastLowIdx, secondLastLowIdx);
        }

        return true;
    }

    private static boolean lowBitsV6V4(byte[] ip, int lastLowIdx, int secondLastLowIdx) {
        for (int i = 0; i < secondLastLowIdx; ++i) {
            if (ip[i] != 0)
                return false;
        }
        if (ip[lastLowIdx] == 0) {
            return ip[secondLastLowIdx] == 0;
        } else if (ip[lastLowIdx] == ((byte) 0b11111111)) {
            return ip[secondLastLowIdx] == ((byte) 0b11111111);
        } else
            return false;
    }

    public static void parseAddress(String address, Callback<byte[], IllegalArgumentException> cb) {
        Resolver.getDefault().resolve(address, new Callback<InetAddress, UnknownHostException>() {
            @Override
            protected void onSucceeded(InetAddress value) {
                cb.succeeded(value.getAddress());
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                cb.failed(new IllegalArgumentException("unknown ip " + address));
            }
        });
    }

    public static byte[] blockParseAddress(String address) throws IllegalArgumentException {
        BlockCallback<byte[], IllegalArgumentException> cb = new BlockCallback<>();
        parseAddress(address, cb);
        return cb.block();
    }

    public static byte[] parseMask(int mask) {
        if (mask > 128) { // mask should not greater than 128
            throw new IllegalArgumentException("unknown mask " + mask);
        } else if (mask > 32) {
            // ipv6
            return getMask(new byte[16], mask);
        } else {
            // ipv4
            return getMask(new byte[4], mask);
        }
        // maybe the mask <= 32 but is for ipv6
        // in this case, we handle it as an ipv4 mask
        // and do some additional work when
        // checking and comparing
    }

    // fill bytes into the `masks` array
    private static byte[] getMask(byte[] masks, int mask) {
        // because java always fill the byte with 0
        // so we only need to set 1 into the bit sequence
        // start from the first bit
        for (int i = 0; i < masks.length; ++i) {
            masks[i] = getByte(mask > 8
                ? 8 // a byte can contain maximum 8 bits
                : mask // it's ok if mask < 0, see comment in getByte()
            );
            // the `to-do` bit sequence moves 8 bits forward each round
            // so subtract 8 from the integer represented mask
            mask -= 8;
        }
        return masks;
    }

    // specify the number of 1 in the head of bit sequence
    // and return a byte
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
                // if <= 0, return 0
                // the `getMask()` method can be more simple
                return 0;
        }
    }
}
