package io.vproxy.vfd;

import io.vproxy.base.dns.Resolver;
import io.vproxy.base.util.*;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.callback.Callback;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;

public abstract class IP implements ToByteArray {
    public static IP from(InetAddress ip) {
        return from(ip.getAddress());
    }

    public static IP from(byte[] arr) {
        if (arr.length == 4) {
            return new IPv4(arr);
        } else if (arr.length == 16) {
            return new IPv6(arr);
        } else {
            throw new IllegalArgumentException("unknown ip address");
        }
    }

    public static IP from(String ip) {
        byte[] bytes = parseIpString(ip);
        if (bytes == null) {
            throw new IllegalArgumentException("input is not a valid ip string");
        }
        return from(bytes);
    }

    public static IP blockResolve(String hostOrIp) {
        return from(blockParseAddress(hostOrIp));
    }

    public static IPv4 fromIPv4(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("input is not a valid ipv4 address");
        }
        return new IPv4(bytes);
    }

    public static IPv4 fromIPv4(String ip) {
        byte[] bytes = parseIpv4String(ip);
        if (bytes == null) {
            throw new IllegalArgumentException("input is not a valid ipv4 string");
        }
        return fromIPv4(bytes);
    }

    public static IPv6 fromIPv6(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("input is not a valid ipv6 address");
        }
        return new IPv6(bytes);
    }

    public static IPv6 fromIPv6(String ip) {
        byte[] bytes = parseIpv6String(ip);
        if (bytes == null) {
            throw new IllegalArgumentException("input is not a valid ipv6 string");
        }
        return fromIPv6(bytes);
    }

    public final ByteArray bytes;

    IP(ByteArray bytes) {
        this.bytes = bytes.unmodifiable();
    }

    @Override
    public ByteArray toByteArray() {
        return bytes;
    }

    public boolean isAnyLocalAddress() {
        for (int i = 0; i < bytes.length(); ++i) {
            byte b = bytes.get(i);
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isLinkLocalAddress() {
        if (this instanceof IPv6) {
            return bytes.get(0) == (byte) 0xfe && (bytes.get(1) & 0b11000000) == 0x80;
        } else {
            return false;
        }
    }

    public byte[] getAddress() {
        return bytes.toJavaArray();
    }

    public InetAddress toInetAddress() {
        return l3addr(getAddress());
    }

    public String formatToIPString() {
        return ipStr(bytes.toJavaArray());
    }

    @Override
    public String toString() {
        return "/" + formatToIPString(); // compatible with java InetAddress
    }

    @Override
    abstract public boolean equals(Object o);

    @Override
    abstract public int hashCode();

    // BEGIN UTILS:
    // return null if not ip literal
    public static byte[] parseIpString(String ip) {
        if (ip.contains(":"))
            return parseIpv6String(ip);
        else
            return parseIpv4String(ip);
    }

    // return null if not ipv4
    public static byte[] parseIpv4String(String ipv4) {
        byte[] ipBytes = Utils.allocateByteArrayInitZero(4);
        if (parseIpv4String(ipv4, ipBytes, 0) == -1) {
            return null; // wrong len or parse fail
        } else {
            return ipBytes;
        }
    }

    private static int parseIpv4String(String ipv4, byte[] ipBytes, int fromIdx) {
        String[] split = Utils.split(ipv4, ".");
        if (split.length != 4)
            return -1; // wrong len
        for (int i = 0; i < split.length; ++i) {
            int idx = fromIdx + i;
            if (idx >= ipBytes.length) {
                return -1; // too long
            }
            String s = split[i];
            char[] digits = s.toCharArray();
            if (digits.length > 3 || digits.length == 0) {
                return -1; // invalid for a byte
            }
            for (char c : digits) {
                if (c < '0' || c > '9')
                    return -1; // should be decimal digits
            }
            if (s.startsWith("0") && s.length() > 1)
                return -1; // 0n is invalid
            int num = Integer.parseInt(s);
            if (num > 255)
                return -1; // invalid byte
            ipBytes[idx] = (byte) num;
        }
        return split.length;
    }

    // return null if not ipv6
    public static byte[] parseIpv6String(String ipv6) {
        if (ipv6.startsWith("[") && ipv6.endsWith("]")) {
            ipv6 = ipv6.substring(1, ipv6.length() - 1);
        }
        { // check how many ::
            int count = Utils.split(ipv6, "::").length - 1;
            if (count > 1)
                return null; // at most 1
        }
        boolean hasDblColon;
        String colonOnly;
        String colonAndDot;
        {
            int idx = ipv6.indexOf("::");
            if (idx == -1) {
                hasDblColon = false;
                colonOnly = null;
                colonAndDot = ipv6;
            } else {
                hasDblColon = true;
                colonOnly = ipv6.substring(0, idx);
                colonAndDot = ipv6.substring(idx + "::".length());
            }
        }
        byte[] ipBytes = Utils.allocateByteArrayInitZero(16);
        int consumed = parseIpv6ColonPart(colonOnly, ipBytes, 0);
        if (consumed == -1)
            return null; // parse failed
        int consumed2 = parseIpv6LastBits(colonAndDot, ipBytes);
        if (consumed2 == -1)
            return null; // parse failed
        if (hasDblColon) {
            if (consumed + consumed2 >= 16)
                return null; // wrong len
        } else {
            if (consumed + consumed2 != 16)
                return null; // wrong len
        }
        return ipBytes;
    }

    // -1 for fail
    private static int parseIpv6ColonPart(String s, byte[] ipBytes, int fromIdx) {
        if (s == null || s.isEmpty())
            return 0;
        if (fromIdx < 0)
            return -1;
        // only hex number splited by `:`
        String[] split = Utils.split(s, ":");
        for (int i = 0; i < split.length; ++i) {
            int baseIndex = fromIdx + 2 * i; // every field occupy 2 bytes
            if (baseIndex >= ipBytes.length) {
                return -1; // too long
            }
            char[] field = split[i].toCharArray();
            if (field.length > 4) {
                // four hexadecimal digits
                return -1;
            }
            if (field.length == 0) {
                // there must be at least one numeral in every field
                return -1;
            }
            for (char c : field) {
                if ((c < 'A' || c > 'F') && (c < 'a' || c > 'f') && (c < '0' || c > '9')) {
                    // hexadecimal
                    return -1;
                }
            }
            switch (field.length) {
                case 1:
                    ipBytes[baseIndex + 1] = (byte) Integer.parseInt(field[0] + "", 16);
                    break;
                case 2:
                    ipBytes[baseIndex + 1] = (byte) Integer.parseInt(field[0] + "" + field[1], 16);
                    break;
                case 3:
                    ipBytes[baseIndex] = (byte) Integer.parseInt(field[0] + "", 16);
                    ipBytes[baseIndex + 1] = (byte) Integer.parseInt(field[1] + "" + field[2], 16);
                    break;
                case 4:
                    ipBytes[baseIndex] = (byte) Integer.parseInt(field[0] + "" + field[1], 16);
                    ipBytes[baseIndex + 1] = (byte) Integer.parseInt(field[2] + "" + field[3], 16);
                    break;
                default:
                    // should not happen
                    return -1;
            }
        }
        return split.length * 2;
    }

    // -1 for fail
    private static int parseIpv6LastBits(String s, byte[] ipBytes) {
        if (s.contains(".")) {
            int idx = s.indexOf('.');
            idx = s.lastIndexOf(':', idx);
            if (idx == -1) {
                return parseIpv4String(s, ipBytes, ipBytes.length - 4);
            } else {
                String colonPart = s.substring(0, idx);
                String dotPart = s.substring(idx + 1);
                int r = parseIpv4String(dotPart, ipBytes, ipBytes.length - 4);
                if (r == -1) {
                    return -1; // wrong len or parse failed
                }
                return 4 + parseIpv6ColonPart(colonPart, ipBytes, ipBytes.length - 4 - Utils.split(colonPart, ":").length * 2);
            }
        } else {
            return parseIpv6ColonPart(s, ipBytes, ipBytes.length - Utils.split(s, ":").length * 2);
        }
    }

    public static byte[] parseIpv4StringConsiderV6Compatible(String s) {
        byte[] v6 = parseIpv6String(s);
        if (v6 != null) {
            // maybe v4-compatible ipv6
            for (int i = 0; i < 10; ++i) {
                if (0 != v6[i])
                    return null;
            }
            for (int i = 11; i < 12; ++i) {
                if ((byte) 0xFF != v6[i])
                    return null;
            }
            byte[] v4 = Utils.allocateByteArrayInitZero(4);
            System.arraycopy(v6, 12, v4, 0, 4);
            return v4;
        }
        return parseIpv4String(s);
    }

    public static boolean isIpv4(String s) {
        return parseIpv4StringConsiderV6Compatible(s) != null;
    }

    public static boolean isIpv6(String s) {
        return parseIpv6String(s) != null;
    }

    public static boolean isIpLiteral(String s) {
        return isIpv4(s) || isIpv6(s);
    }

    public static IPPort blockParseL4Addr(String l4addr) throws Exception {
        if (!l4addr.contains(":")) {
            throw new Exception("missing port");
        }
        String ip = l4addr.substring(0, l4addr.lastIndexOf(":"));
        String portStr = l4addr.substring(l4addr.lastIndexOf(":") + 1);
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new Exception("invalid port number");
        }
        if (port < 0 || port > 65535) {
            throw new Exception("invalid port number: out of range");
        }

        byte[] ipBytes = parseIpString(ip);
        if (ipBytes != null) {
            return new IPPort(IP.from(ipBytes), port);
        }
        // try hostname
        var cb = new BlockCallback<IP, IOException>();
        Resolver.getDefault().resolve(ip, cb);
        return new IPPort(cb.block(), port);
    }

    public static IP getInetAddressFromNic(String nicName, IPType ipType) throws SocketException {
        InetAddress bindInetAddress;
        Inet4Address v4Addr = null;
        Inet6Address v6Addr = null;
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nicName.equals(nic.getDisplayName())) {
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress a = addresses.nextElement();
                    if (a instanceof Inet4Address) {
                        v4Addr = (Inet4Address) a;
                    } else if (a instanceof Inet6Address) {
                        v6Addr = (Inet6Address) a;
                    }
                    if (v4Addr != null && v6Addr != null)
                        break; // both v4 and v6 found
                }
                break; // nic found, so break
            }
        }
        if (v4Addr == null && v6Addr == null)
            throw new SocketException("nic " + nicName + " not found or no ip address");
        if (ipType == IPType.v4 && v4Addr == null)
            throw new SocketException("nic " + nicName + " do not have a v4 ip");
        if (ipType == IPType.v6 && v6Addr == null)
            throw new SocketException("nic " + nicName + " do not have a v6 ip");
        if (ipType == IPType.v4) {
            //noinspection ConstantConditions
            assert v4Addr != null;
            bindInetAddress = v4Addr;
        } else {
            assert v6Addr != null;
            bindInetAddress = v6Addr;
        }

        return IP.from(bindInetAddress);
    }

    private static InetAddress l3addr(byte[] addr) {
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            StringBuilder err = new StringBuilder("creating l3addr from ");
            boolean isFirst = true;
            for (byte x : addr) {
                if (isFirst) isFirst = false;
                else err.append(".");
                err.append(x & 0xff);
            }
            err.append(" failed");
            Logger.shouldNotHappen(err.toString(), e);
            throw new RuntimeException(e);
        }
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

    public static String ipv6Str(byte[] ip) {
        String[] split = {Integer.toHexString((((ip[0] & 0xFF) << 8) & 0xFFFF) | (ip[1] & 0xFF)),
            Integer.toHexString((((ip[2] & 0xFF) << 8) & 0xFFFF) | (ip[3] & 0xFF)),
            Integer.toHexString((((ip[4] & 0xFF) << 8) & 0xFFFF) | (ip[5] & 0xFF)),
            Integer.toHexString((((ip[6] & 0xFF) << 8) & 0xFFFF) | (ip[7] & 0xFF)),
            Integer.toHexString((((ip[8] & 0xFF) << 8) & 0xFFFF) | (ip[9] & 0xFF)),
            Integer.toHexString((((ip[10] & 0xFF) << 8) & 0xFFFF) | (ip[11] & 0xFF)),
            Integer.toHexString((((ip[12] & 0xFF) << 8) & 0xFFFF) | (ip[13] & 0xFF)),
            Integer.toHexString((((ip[14] & 0xFF) << 8) & 0xFFFF) | (ip[15] & 0xFF)),
        };

        int max = 0;
        int idx = split.length;
        int endExclusive = 0;
        int curLen = 0;
        int curIdx = -1;
        for (int i = 0; i <= split.length; i++) {
            String s;
            if (i < split.length) {
                s = split[i];
            } else {
                s = ""; // not 0
            }
            if (s.equals("0")) {
                if (curIdx == -1) { // not started yet
                    curIdx = i;
                }
                curLen += 1;
            } else {
                if (curIdx != -1) { // end
                    if (max < curLen) {
                        max = curLen;
                        idx = curIdx;
                        endExclusive = i;
                    }
                    curLen = 0;
                    curIdx = -1;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < split.length; ++i) {
            if (i < idx || i >= endExclusive) {
                if (i != 0 && i != endExclusive) {
                    sb.append(":");
                }
                sb.append(split[i]);
            } else if (i == idx) {
                sb.append("::");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String ipv4Str(byte[] ip) {
        return Utils.positive(ip[0]) + "." + Utils.positive(ip[1]) + "." + Utils.positive(ip[2]) + "." + Utils.positive(ip[3]);
    }

    public static void parseAddress(String address, Callback<byte[], IllegalArgumentException> cb) {
        Resolver.getDefault().resolve(address, new Callback<>() {
            @Override
            protected void onSucceeded(IP value) {
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

    public static int ipv4Bytes2Int(byte[] host) {
        return ((host[0] & 0xff) << 24) | ((host[1] & 0xff) << 16) | ((host[2] & 0xff) << 8) | (host[3] & 0xff);
    }

    public static byte[] ipv4Int2Bytes(int ip) {
        byte[] ret = Utils.allocateByteArrayInitZero(4);
        ret[0] = (byte) ((ip >> 24) & 0xff);
        ret[1] = (byte) ((ip >> 16) & 0xff);
        ret[2] = (byte) ((ip >> 8) & 0xff);
        ret[3] = (byte) ((ip) & 0xff);
        return ret;
    }

    public abstract boolean isBroadcast();

    public abstract boolean isMulticast();

    public boolean isUnicast() {
        return !isMulticast() && !isBroadcast();
    }
}
