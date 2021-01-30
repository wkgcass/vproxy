package vproxybase.util;

import vfd.FDProvider;
import vpacket.Ipv4Packet;
import vpacket.Ipv6Packet;
import vproxybase.util.thread.VProxyThread;
import vproxybase.util.unsafe.JDKUnsafe;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Utils {
    public static final List<String> RESET_MSG = Arrays.asList(
        "Connection reset by peer",
        "Connection reset"
    );
    public static final String BROKEN_PIPE_MSG = "Broken pipe";
    public static final String SSL_ENGINE_CLOSED_MSG = "SSLEngine closed";
    public static final String HOST_IS_DOWN_MSG = "Host is down";
    public static final String NO_ROUTE_TO_HOST_MSG = "No route to host";
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

    public static String homedir() {
        return System.getProperty("user.home");
    }

    public static String filename(String s) {
        if (s.startsWith("~")) {
            s = homedir() + s.substring(1);
        }
        return s;
    }

    public static String homefile(String s) {
        return homedir() + File.separator + s;
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

    private static String formatErrBase(Throwable err) {
        if (err.getMessage() != null && !err.getMessage().isBlank()) {
            return err.getMessage().trim();
        } else {
            return err.toString();
        }
    }

    public static String formatErr(Throwable err) {
        String base = formatErrBase(err);
        if (err instanceof RuntimeException) {
            return base + Arrays.asList(err.getStackTrace()).toString();
        } else {
            return base;
        }
    }

    public static int zeros(byte b) {
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

    public static byte[] long2bytes(long v) {
        LinkedList<Byte> bytes = new LinkedList<>();
        while (v != 0) {
            byte b = (byte) (v & 0xff);
            bytes.addFirst(b);
            v = v >> 8;
        }
        byte[] ret = allocateByteArray(bytes.size());
        int idx = 0;
        for (byte b : bytes) {
            ret[idx] = b;
            ++idx;
        }
        return ret;
    }

    public static boolean lowBitsV6V4(byte[] ip, int lastLowIdx, int secondLastLowIdx) {
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

    // specify the number of 1 in the head of bit sequence
    // and return a byte
    public static byte getByte(int ones) {
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
        return RESET_MSG.contains(t.getMessage());
    }

    public static boolean isBrokenPipe(IOException t) {
        return BROKEN_PIPE_MSG.equals(t.getMessage());
    }

    public static boolean isSSLEngineClosed(IOException t) {
        return SSL_ENGINE_CLOSED_MSG.equals(t.getMessage());
    }

    public static boolean isTerminatedIOException(IOException t) {
        return isReset(t) || isBrokenPipe(t) || isSSLEngineClosed(t);
    }

    public static boolean isHostIsDown(IOException t) {
        return HOST_IS_DOWN_MSG.equals(t.getMessage());
    }

    public static boolean isNoRouteToHost(IOException t) {
        return NO_ROUTE_TO_HOST_MSG.equals(t.getMessage());
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

    public static byte[] hexToBytes(String hex) {
        char[] chars = hex.toCharArray();
        if (chars.length % 2 != 0) throw new IllegalArgumentException("invalid hex string");
        byte[] ret = allocateByteArray(chars.length / 2);
        for (int i = 0; i < chars.length; i += 2) {
            char m = chars[i];
            char n = chars[i + 1];
            byte b = (byte) ((parseHexChar(m) << 4) | parseHexChar(n));
            ret[i / 2] = b;
        }
        return ret;
    }

    private static byte parseHexChar(char c) {
        if ((c < '0' || c > '9') && (c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
            throw new IllegalArgumentException("char `" + c + "' cannot bev hex");
        }
        //noinspection ConstantConditions
        if ('0' <= c && c <= '9') {
            return (byte) (c - '0');
        }
        //noinspection ConstantConditions
        if ('a' <= c && c <= 'z') {
            return (byte) (c - 'a' + 10);
        }
        return (byte) (c - 'A' + 10);
    }

    public static boolean debug(Runnable r) {
        //noinspection ConstantConditions,TrivialFunctionalExpressionUsage
        assert ((BooleanSupplier) () -> {
            r.run();
            return true;
        }).getAsBoolean();
        return true;
    }

    public static byte[] gzipCompress(ByteArrayOutputStream baos, byte[] plain) {
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos) {
            {
                this.def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }) {
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
            byte[] buf = allocateByteArray(1024);
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

    private static final int UNINITIALIZED_BYTE_ARRAY_THRESHOLD = 512;

    public static byte[] allocateByteArray(int len) {
        if (len < UNINITIALIZED_BYTE_ARRAY_THRESHOLD)
            return allocateByteArrayInitZero(len);
        return JDKUnsafe.allocateUninitializedByteArray(len);
    }

    public static byte[] allocateByteArrayInitZero(int len) {
        return new byte[len];
    }

    private static final byte[] ZERO_LENGTH_BYTE_ARRAY = new byte[0];

    public static byte[] getZeroLengthByteArray() {
        return ZERO_LENGTH_BYTE_ARRAY;
    }

    public static ByteBuffer allocateByteBuffer(int cap) {
        return ByteBuffer.wrap(allocateByteArray(cap));
    }

    public interface UtilSupplier<T> {
        T get() throws Exception;
    }

    public static <T> T runAvoidNull(Supplier<T> f, T dft) {
        try {
            return f.get();
        } catch (NullPointerException e) {
            return dft;
        }
    }

    public static String toHexString(int x) {
        return "0x" + Integer.toHexString(x);
    }

    public static String toHexStringWithPadding(int x, int bits) {
        assert bits % 8 == 0;
        int len = bits / 4;
        String s = Integer.toHexString(x);
        if (s.length() < len) {
            s = "0".repeat(len - s.length()) + s;
        }
        return "0x" + s;
    }

    public static String toBinaryString(int x) {
        return "0b" + Integer.toBinaryString(x);
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static void pipeOutputOfSubProcess(Process p) {
        var stdout = p.getInputStream();
        var stderr = p.getErrorStream();
        pipeOutputOfStream(stdout, "stdout");
        pipeOutputOfStream(stderr, "stderr");
    }

    private static void pipeOutputOfStream(InputStream stdout, String descr) {
        VProxyThread.create(() -> {
            var br = new BufferedReader(new InputStreamReader(stdout));
            String x;
            try {
                while ((x = br.readLine()) != null) {
                    System.out.println(x);
                }
            } catch (Throwable ignore) {
            }
            try {
                stdout.close();
            } catch (Throwable ignore) {
            }
        }, "pipe-output-of-stream-" + descr).start();
    }

    // the returned array would be without getStackTrace() and this method
    public static StackTraceElement[] stackTraceStartingFromThisMethodInclusive() {
        final String meth = "stackTraceStartingFromThisMethodInclusive";
        StackTraceElement[] arr = Thread.currentThread().getStackTrace();
        int i = 0;
        for (StackTraceElement elem : arr) {
            i += 1;
            if (elem.getMethodName().equals(meth)) {
                break;
            }
        }
        StackTraceElement[] ret = new StackTraceElement[arr.length - i];
        System.arraycopy(arr, i, ret, 0, ret.length);
        return ret;
    }

    public static void exit(int code) {
        System.exit(code);
    }

    public static ByteArray buildPseudoIPv4Header(Ipv4Packet ipv4, int upperType, int upperLength) {
        ByteArray pseudoHeaderTail = ByteArray.allocate(8);
        ByteArray pseudoHeader = ByteArray.from(ipv4.getSrc().getAddress())
            .concat(ByteArray.from(ipv4.getDst().getAddress()))
            .concat(pseudoHeaderTail);
        pseudoHeaderTail.set(1, (byte) upperType);
        pseudoHeaderTail.int16(2, upperLength);
        return pseudoHeader;
    }

    public static ByteArray buildPseudoIPv6Header(Ipv6Packet ipv6, int upperType, int upperLength) {
        ByteArray pseudoHeaderTail = ByteArray.allocate(8);
        ByteArray pseudoHeader = ByteArray.from(ipv6.getSrc().getAddress())
            .concat(ByteArray.from(ipv6.getDst().getAddress()))
            .concat(pseudoHeaderTail);
        pseudoHeaderTail.int32(0, upperLength);
        pseudoHeaderTail.set(7, (byte) upperType);
        return pseudoHeader;
    }

    public static int calculateChecksum(ByteArray array, int limit) {
        int sum = 0;
        for (int i = 0; i < limit / 2; ++i) {
            sum += array.uint16(i * 2);
            while (sum > 0xffff) {
                sum = (sum & 0xffff) + 1;
            }
        }
        if (limit % 2 != 0) {
            sum += (array.uint8(limit - 1) << 8);
            while (sum > 0xffff) {
                sum = (sum & 0xffff) + 1;
            }
        }
        return 0xffff - sum;
    }

    public static byte[] sha1(byte[] input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            Logger.shouldNotHappen("SHA-1 not found");
            throw new RuntimeException(e);
        }
        md.update(input);
        return md.digest();
    }
}
