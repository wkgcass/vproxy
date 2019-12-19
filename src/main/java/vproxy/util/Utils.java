package vproxy.util;

import sun.misc.Unsafe;
import vfd.FDProvider;
import vproxy.connection.Connector;
import vproxy.dns.Resolver;
import vproxy.socks.AddressType;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Utils {
    public static final String RESET_MSG = "Connection reset by peer";
    public static final String BROKEN_PIPE_MSG = "Broken pipe";
    @SuppressWarnings("unused")
    private static volatile int sync = 0; // this filed is used to sync cpu cache into memory

    private Utils() {
    }

    public static void syncCpuCacheAndMemory() {
        //noinspection NonAtomicOperationOnVolatileField
        ++sync;
    }

    public static int positive(byte b) {
        if (b < 0) return 256 + b;
        return b;
    }

    public static int positive(short s) {
        if (s < 0) return 32768 + s;
        return s;
    }

    private static String addTo(@SuppressWarnings("SameParameterValue") int len, String s) {
        if (s.length() >= len)
            return s;
        StringBuilder sb = new StringBuilder();
        //noinspection StringRepeatCanBeUsed
        for (int i = s.length(); i < len; ++i) {
            sb.append("0");
        }
        sb.append(s);
        return sb.toString();
    }

    public static String ipport(InetSocketAddress addr) {
        return ipStr(addr.getAddress().getAddress()) + ":" + addr.getPort();
    }

    private static String ipv6Str(byte[] ip) {
        return "[" + addTo(4, Integer.toHexString((((ip[0] & 0xFF) << 8) & 0xFFFF) | (ip[1] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[2] & 0xFF) << 8) & 0xFFFF) | (ip[3] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[4] & 0xFF) << 8) & 0xFFFF) | (ip[5] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[6] & 0xFF) << 8) & 0xFFFF) | (ip[7] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[8] & 0xFF) << 8) & 0xFFFF) | (ip[9] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[10] & 0xFF) << 8) & 0xFFFF) | (ip[11] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[12] & 0xFF) << 8) & 0xFFFF) | (ip[13] & 0xFF)))
            + ":" + addTo(4, Integer.toHexString((((ip[14] & 0xFF) << 8) & 0xFFFF) | (ip[15] & 0xFF)))
            + "]";
    }

    private static String ipv4Str(byte[] ip) {
        return positive(ip[0]) + "." + positive(ip[1]) + "." + positive(ip[2]) + "." + positive(ip[3]);
    }

    public static String l4addrStr(InetSocketAddress l4addr) {
        return ipStr(l4addr.getAddress().getAddress()) + ":" + l4addr.getPort();
    }

    public static String ipStr(byte[] ip) {
        { // return 0.0.0.0 if all zero (instead of ipv6 ::)
            boolean allZero = true;
            for (int i : ip) {
                if (i != 0) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                return "0.0.0.0";
            }
        }

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
        Resolver.getDefault().resolve(address, new Callback<>() {
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
            //noinspection ManualMinMaxCalculation
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

    public static InetAddress getInetAddressFromNic(String nicName, IPType ipType) throws SocketException {
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

        return bindInetAddress;
    }

    public static String[] split(String str, String e) {
        List<String> ls = new LinkedList<>();
        int idx = -e.length();
        int lastIdx = 0;
        while (true) {
            idx = str.indexOf(e, idx + e.length());
            if (idx == -1) {
                ls.add(str.substring(lastIdx));
                break;
            }
            ls.add(str.substring(lastIdx, idx));
            lastIdx = idx + e.length();
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return ls.toArray(new String[ls.size()]);
    }

    // return null if not ip literal
    public static byte[] parseIpString(String ip) {
        if (ip.contains(":"))
            return parseIpv6String(ip);
        else
            return parseIpv4String(ip);
    }

    // return null if not ipv4
    public static byte[] parseIpv4String(String ipv4) {
        byte[] ipBytes = new byte[4];
        if (parseIpv4String(ipv4, ipBytes, 0) == -1) {
            return null; // wrong len or parse fail
        } else {
            return ipBytes;
        }
    }

    private static int parseIpv4String(String ipv4, byte[] ipBytes, int fromIdx) {
        String[] split = split(ipv4, ".");
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
            int count = split(ipv6, "::").length - 1;
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
        byte[] ipBytes = new byte[16];
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
        String[] split = split(s, ":");
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
                return 4 + parseIpv6ColonPart(colonPart, ipBytes, ipBytes.length - 4 - split(colonPart, ":").length * 2);
            }
        } else {
            return parseIpv6ColonPart(s, ipBytes, ipBytes.length - split(s, ":").length * 2);
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
            byte[] v4 = new byte[4];
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

    private static Unsafe U;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            U = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logger.shouldNotHappen("Reflection failure: get unsafe failed " + e);
            throw new RuntimeException(e);
        }
    }

    public static void clean(ByteBuffer buffer) {
        assert Logger.lowLevelDebug("run Utils.clean");
        if (!buffer.getClass().getName().equals("java.nio.DirectByteBuffer")) {
            assert Logger.lowLevelDebug("not direct buffer");
            return;
        }
        assert Logger.lowLevelDebug("is direct buffer, do clean");
        U.invokeCleaner(buffer);
    }

    public static void directConnect(AddressType type, String address, int port, Consumer<Connector> providedCallback) {
        if (type == AddressType.domain) { // resolve if it's domain
            Resolver.getDefault().resolve(address, new Callback<>() {
                @Override
                protected void onSucceeded(InetAddress value) {
                    providedCallback.accept(new Connector(new InetSocketAddress(value, port)));
                }

                @Override
                protected void onFailed(UnknownHostException err) {
                    // resolve failed
                    assert Logger.lowLevelDebug("resolve for " + address + " failed in socks5 server" + err);
                    providedCallback.accept(null);
                }
            });
        } else {
            if (!Utils.isIpLiteral(address)) {
                assert Logger.lowLevelDebug("client request with an invalid ip " + address);
                providedCallback.accept(null);
                return;
            }
            InetAddress remote;
            try {
                remote = InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                // should not happen when retrieving from an ip address
                Logger.shouldNotHappen("getting " + address + " failed", e);
                providedCallback.accept(null);
                return;
            }
            providedCallback.accept(new Connector(new InetSocketAddress(remote, port)));
        }
    }

    public static long currentMinute() {
        return
            (FDProvider.get().currentTimeMillis() / 60_000 // remove millis and seconds
            ) * 60_000 // get minutes
            ;
    }

    public static void shiftLeft(byte[] arr, int l) {
        for (int i = 0; i < arr.length; ++i) {
            int e = i + l;
            byte b = e >= arr.length ? 0 : arr[e];
            arr[i] = b;
        }
    }

    public static boolean isReset(IOException t) {
        return RESET_MSG.equals(t.getMessage());
    }

    public static boolean isBrokenPipe(IOException t) {
        return BROKEN_PIPE_MSG.equals(t.getMessage());
    }

    public static Process runSubProcess(String program) throws IOException {
        return Runtime.getRuntime().exec(program);
    }

    private static void proxyProcessOutput(InputStream subProcess, PrintStream output) {
        new Thread(() -> {
            while (true) {
                int intB;
                try {
                    intB = subProcess.read();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                if (intB == -1) {
                    // EOF, so break
                    break;
                }
                byte b = (byte) intB;
                output.write(b);
            }
        }).start();
    }

    public static void proxyProcessOutput(Process process) {
        proxyProcessOutput(process.getInputStream(), System.out);
        proxyProcessOutput(process.getErrorStream(), System.err);
    }

    public static String stackTrace() {
        StringWriter s = new StringWriter();
        new Throwable().printStackTrace(new PrintWriter(s));
        return s.toString();
    }

    public static int writeFromFIFOQueueToBufferPacketBound(Deque<ByteBuffer> bufs, ByteBuffer dst) {
        int ret = 0;
        while (true) {
            if (bufs.isEmpty()) {
                // src is empty
                break;
            }
            ByteBuffer b = bufs.peek();
            int bufLim = b.limit();
            int bufPos = b.position();
            if (bufLim - bufPos == 0) {
                bufs.poll();
                continue;
            }
            int dstLim = dst.limit();
            int dstPos = dst.position();

            if (dstLim - dstPos == 0) {
                // dst is full
                break;
            }

            if (dstLim - dstPos < bufLim - bufPos) {
                // we consider packet bound
                // so should not write partial data into the dst
                break;
            }

            ret += (b.limit() - b.position());
            dst.put(b);
        }
        return ret;
    }

    public static int writeFromFIFOQueueToBuffer(Deque<ByteBuffer> bufs, ByteBuffer dst) {
        int ret = 0;
        while (true) {
            if (bufs.isEmpty()) {
                // src is empty
                break;
            }
            ByteBuffer b = bufs.peek();
            int oldLim = b.limit();
            int oldPos = b.position();
            if (oldLim - oldPos == 0) {
                bufs.poll();
                continue;
            }
            int dstLim = dst.limit();
            int dstPos = dst.position();

            if (dstLim - dstPos == 0) {
                // dst is full
                break;
            }

            if (dstLim - dstPos < oldLim - oldPos) {
                b.limit(oldPos + (dstLim - dstPos));
            }
            ret += (b.limit() - b.position());
            dst.put(b);
            b.limit(oldLim);
        }
        return ret;
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static boolean debug(Runnable r) {
        //noinspection ConstantConditions,TrivialFunctionalExpressionUsage
        assert ((BooleanSupplier) () -> {
            r.run();
            return true;
        }).getAsBoolean();
        return true;
    }

    public static int ipv4Bytes2Int(byte[] host) {
        return ((host[0] & 0xff) << 24) | ((host[1] & 0xff) << 16) | ((host[2] & 0xff) << 8) | (host[3] & 0xff);
    }

    public static byte[] ipv4Int2Bytes(int ip) {
        byte[] ret = new byte[4];
        ret[0] = (byte) ((ip >> 24) & 0xff);
        ret[1] = (byte) ((ip >> 16) & 0xff);
        ret[2] = (byte) ((ip >> 8) & 0xff);
        ret[3] = (byte) ((ip) & 0xff);
        return ret;
    }

    public static byte[] gzipCompress(ByteArrayOutputStream baos, byte[] plain) {
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(plain);
        } catch (IOException e) {
            Logger.shouldNotHappen("running gzip compression failed", e);
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static byte[] gzipDecompress(ByteArrayOutputStream baos, byte[] compressed) {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = gzip.read(buf, 0, buf.length)) >= 0) {
                baos.write(buf, 0, n);
            }
        } catch (IOException e) {
            Logger.shouldNotHappen("running gzip decompression failed", e);
            return null;
        }
        return baos.toByteArray();
    }

    public static boolean assertOn() {
        try {
            assert false;
            return false;
        } catch (AssertionError ignore) {
            return true;
        }
    }
}
