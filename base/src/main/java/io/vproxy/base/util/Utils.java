package io.vproxy.base.util;

import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.base.util.net.Nic;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.base.util.unsafe.JDKUnsafe;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.Ipv4Packet;
import io.vproxy.vpacket.Ipv6Packet;

import java.io.*;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private static final boolean assertOn;

    static {
        boolean _assertOn;
        try {
            assert false;
            _assertOn = false;
        } catch (AssertionError ignore) {
            _assertOn = true;
        }
        //noinspection ConstantConditions
        assertOn = _assertOn;
    }

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

    private static final Set<Class<?>> hideClassNameExceptions = Set.of(
        Exception.class,
        XException.class,
        RuntimeException.class,
        Error.class,
        Throwable.class,
        AlreadyExistException.class,
        NotFoundException.class);

    private static String formatErrBase(Throwable err) {
        if (err.getMessage() != null && !err.getMessage().isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (!hideClassNameExceptions.contains(err.getClass())) {
                sb.append(err.getClass().getSimpleName()).append(": ");
            }
            sb.append(err.getMessage().trim());
            return sb.toString();
        } else {
            return err.toString();
        }
    }

    public static String formatErr(Throwable err) {
        String base = formatErrBase(err);
        if (err instanceof RuntimeException || Logger.stackTraceOn) {
            return base + Arrays.asList(err.getStackTrace());
        } else {
            return base;
        }
    }

    public static <T> String formatArrayToStringCompact(T[] arr) {
        if (arr.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (T s : arr) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(",");
            }
            sb.append(s);
        }
        return sb.toString();
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
    public static byte genPrefixByte(int ones) {
        switch (ones) {
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
                if (ones >= 8) {
                    return (byte) 0b11111111;
                } else {
                    return 0;
                }
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

    public static byte[] binToBytes(String bin) {
        char[] chars = bin.toCharArray();
        if (chars.length % 8 != 0) throw new IllegalArgumentException("invalid bin string");
        byte[] ret = allocateByteArray(chars.length / 8);
        for (int i = 0; i < chars.length; i += 8) {
            int b = 0;
            for (int x = 0; x < 8; ++x) {
                char c = chars[i + x];
                if (c != '1' && c != '0') throw new IllegalArgumentException("char `" + c + "` cannot be bin");
                int n = c == '1' ? 1 : 0;
                b |= n << (7 - x);
            }
            ret[i / 8] = (byte) b;
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
            throw new IllegalArgumentException("char `" + c + "' cannot be hex");
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

    public static boolean allZerosAfter(ByteArray bytes, int index) {
        for (int i = index; i < bytes.length(); ++i) {
            if (bytes.get(i) != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean assertOn() {
        return assertOn;
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
        String s = Integer.toHexString(x);
        if (s.length() % 2 == 0) {
            return "0x" + s;
        } else {
            return "0x0" + s;
        }
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

    public static boolean isNonNegativeInteger(String s) {
        int n;
        try {
            n = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return n >= 0;
    }

    public static boolean isPositiveInteger(String s) {
        int n;
        try {
            n = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return n > 0;
    }

    public static boolean isLong(String s) {
        try {
            Long.parseLong(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static void execute(String script) throws Exception {
        execute(script, false);
    }

    public static ExecuteResult execute(String script, boolean getResult) throws Exception {
        return execute(script, 10 * 1000, getResult);
    }

    public static void execute(String script, int timeout) throws Exception {
        execute(script, timeout, false);
    }

    public static ExecuteResult execute(String script, int timeout, boolean getResult) throws Exception {
        if (script.contains("\n")) {
            Logger.alert("trying to execute script:\n" + script);
        } else {
            Logger.alert("trying to execute script: " + script);
        }
        File file = File.createTempFile("script", OS.isWindows() ? ".bat" : ".sh");
        try {
            Files.writeString(file.toPath(), script);
            if (!file.setExecutable(true)) {
                throw new Exception("setting executable to script " + file.getAbsolutePath() + " failed");
            }
            ProcessBuilder pb;
            if (OS.isWindows()) {
                pb = new ProcessBuilder("cmd.exe", "/c", file.getAbsolutePath());
            } else {
                pb = new ProcessBuilder(file.getAbsolutePath());
            }
            return execute(pb, timeout, getResult);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static void execute(ProcessBuilder pb, int timeout) throws Exception {
        execute(pb, timeout, false);
    }

    public static ExecuteResult execute(ProcessBuilder pb, int timeout, boolean getResult) throws Exception {
        Process p = pb.start();
        BlockCallback<String, Exception> stdoutCB = new BlockCallback<>();
        BlockCallback<String, Exception> stderrCB = new BlockCallback<>();
        if (getResult) {
            Utils.readOutputOfSubProcess(p, stdoutCB, stderrCB);
        } else {
            Utils.pipeOutputOfSubProcess(p);
        }
        p.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (p.isAlive()) {
            p.destroyForcibly();
            throw new Exception("the process took too long to execute");
        }
        int exit = p.exitValue();
        if (getResult) {
            return new ExecuteResult(exit, stdoutCB.block(), stderrCB.block());
        }
        if (exit == 0) {
            return null;
        }
        throw new Exception("exit code is " + exit);
    }

    public static class ExecuteResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ExecuteResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public String toString() {
            return "ExecuteResult{exitCode=" + exitCode + "\n" +
                "----- stdout -----\n" +
                stdout + "\n" +
                "----- stderr -----\n" +
                stderr + "\n" +
                "}";
        }
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

    public static void readOutputOfSubProcess(Process p,
                                              Callback<String, Exception> stdoutCB,
                                              Callback<String, Exception> stderrCB) {
        var stdout = p.getInputStream();
        var stderr = p.getErrorStream();
        readOutputOfStream(stdout, "stdout", stdoutCB);
        readOutputOfStream(stderr, "stderr", stderrCB);
    }

    private static void readOutputOfStream(InputStream stdout, String descr, Callback<String, Exception> cb) {
        VProxyThread.create(() -> {
            var sb = new StringBuilder();
            var reader = new InputStreamReader(stdout);
            char[] buf = new char[1024];
            try {
                int n;
                while ((n = reader.read(buf)) >= 0) {
                    if (n > 0) {
                        sb.append(buf, 0, n);
                    }
                }
            } catch (Throwable ignore) {
            }
            try {
                stdout.close();
            } catch (Throwable ignore) {
            }
            cb.succeeded(sb.toString());
        }, "read-output-of-stream-" + descr).start();
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
        ByteArray pseudoHeaderTail = ByteArray.allocate(4);
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

    public static String getSystemProperty(String key) {
        return getSystemProperty(key, null);
    }

    public static String getSystemProperty(String pattern, String defaultValue) {
        String[] split = pattern.split("_");
        Set<String> results = new LinkedHashSet<>();

        String ret;

        // -Dio.vproxy.AbcDef
        ret = System.getProperty("io.vproxy." + namingConventionPascal(split));
        if (ret != null) {
            results.add(ret);
        }
        // -Dvproxy.AbcDef
        ret = System.getProperty("vproxy." + namingConventionPascal(split));
        if (ret != null) {
            results.add(ret);
        }
        // -Dvproxy_abc_def
        ret = System.getProperty("vproxy_" + namingConventionUnderline(split, false));
        if (ret != null) {
            results.add(ret);
        }
        // -DVPROXY_ABC_DEF
        ret = System.getProperty("VPROXY_" + namingConventionUnderline(split, true));
        if (ret != null) {
            results.add(ret);
        }

        // env: VPROXY_ABC_DEF
        ret = System.getenv("VPROXY_" + namingConventionUnderline(split, true));
        if (ret != null) {
            results.add(ret);
        }

        // use properties with prefix first
        ret = exactlyOneProperty(results);
        if (ret != null) {
            return ret;
        }
        results.clear();

        if (split[0].startsWith("d")) {
            // -Deploy=xxx
            ret = System.getProperty(namingConventionPascal(split).substring(1));
            if (ret != null) {
                results.add(ret);
            }
        }
        // -DAbcDef
        ret = System.getProperty(namingConventionPascal(split));
        if (ret != null) {
            results.add(ret);
        }
        // -DabcDef
        ret = System.getProperty(namingConventionCamel(split));
        if (ret != null) {
            results.add(ret);
        }
        // -Dabc_def
        ret = System.getProperty(namingConventionUnderline(split, false));
        if (ret != null) {
            results.add(ret);
        }
        // -DABC_DEF
        ret = System.getProperty(namingConventionUnderline(split, true));
        if (ret != null) {
            results.add(ret);
        }
        // -Dabcdef
        ret = System.getProperty(namingConventionJoin(split, false));
        if (ret != null) {
            results.add(ret);
        }
        // -DABCDEF
        ret = System.getProperty(namingConventionJoin(split, true));
        if (ret != null) {
            results.add(ret);
        }

        ret = exactlyOneProperty(results);
        if (ret != null) {
            return ret;
        }

        return defaultValue;
    }

    private static String exactlyOneProperty(Set<String> results) {
        if (results.isEmpty()) {
            return null;
        }
        var res = results.iterator().next();
        if (results.size() > 1) {
            Logger.warn(LogType.ALERT,
                "multiple values of keys in different patterns set for the same property: " + results + ", using: " + res);
        }
        return res;
    }

    private static String namingConventionPascal(String[] split) {
        StringBuilder sb = new StringBuilder();
        sb.append(split[0].substring(0, 1).toUpperCase());
        sb.append(split[0].substring(1));
        for (int i = 1; i < split.length; ++i) {
            sb.append(split[i].substring(0, 1).toUpperCase());
            sb.append(split[i].substring(1));
        }
        return sb.toString();
    }

    private static String namingConventionCamel(String[] split) {
        StringBuilder sb = new StringBuilder();
        sb.append(split[0]);
        for (int i = 1; i < split.length; ++i) {
            sb.append(split[i].substring(0, 1).toUpperCase());
            sb.append(split[i].substring(1));
        }
        return sb.toString();
    }

    private static String namingConventionUnderline(String[] split, boolean upper) {
        if (upper) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < split.length; ++i) {
                if (i != 0) {
                    sb.append("_");
                }
                sb.append(split[i].toUpperCase());
            }
            return sb.toString();
        } else {
            return String.join("_", split);
        }
    }

    private static String namingConventionJoin(String[] split, boolean upper) {
        if (upper) {
            StringBuilder sb = new StringBuilder();
            for (String s : split) {
                sb.append(s.toUpperCase());
            }
            return sb.toString();
        } else {
            return String.join("", split);
        }
    }

    public static List<Nic> getNetworkInterfaces() throws IOException {
        var ret = new ArrayList<Nic>();
        if (!OS.isLinux()) {
            var ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                var iface = ifaces.nextElement();
                ret.add(new Nic(iface.getName(), new MacAddress(iface.getHardwareAddress()),
                    -1, iface.isVirtual()));
            }
            return ret;
        }
        String[] lines = Files.readString(Path.of("/proc/net/dev")).split("\n");
        // skip first two lines, they are column names
        for (int i = 2; i < lines.length; ++i) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            line = line.trim();
            int idx = line.indexOf(":");
            if (idx == -1) { // unexpected
                continue;
            }
            String name = line.substring(0, idx);
            File virtualPath = new File("/sys/devices/virtual/net/" + name + "/");
            boolean isVirtual = virtualPath.exists();
            String macStr = Files.readString(Path.of("/sys/class/net/" + name + "/address")).trim();
            MacAddress mac;
            try {
                mac = new MacAddress(macStr);
            } catch (IllegalArgumentException e) {
                if (isVirtual) {
                    // might be tun dev
                    mac = new MacAddress("00:00:00:00:00:00");
                } else {
                    throw new IOException("unable to parse mac for " + name + ": " + macStr, e);
                }
            }
            int speed = -1;
            try {
                String speedStr = Files.readString(Path.of("/sys/class/net/" + name + "/speed")).trim();
                speed = Integer.parseInt(speedStr);
            } catch (Exception ignore) {
            }
            ret.add(new Nic(name, mac, speed, isVirtual));
        }
        return ret;
    }

    public static void loadDynamicLibrary(String name) throws UnsatisfiedLinkError {
        loadDynamicLibrary(name, Utils.class.getClassLoader(), "io/vproxy/");
    }

    public static void loadDynamicLibrary(String name, ClassLoader cl, String basePath) throws UnsatisfiedLinkError {
        // format basePath
        if (basePath.startsWith("/")) {
            basePath = basePath.substring(1);
        }
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        // check and use bundled binaries
        String filename = "lib" + name + "-" + OS.arch();
        String suffix;
        if (OS.isMac()) {
            suffix = ".dylib";
        } else if (OS.isWindows()) {
            filename = name + "-" + OS.arch();
            suffix = ".dll";
        } else {
            suffix = ".so";
        }

        var pathInClasspath = basePath + filename + suffix;
        System.out.print("checking classpath " + pathInClasspath + " for dynamic library: ");
        InputStream is = cl.getResourceAsStream(pathInClasspath);
        if (is == null) {
            System.out.println("missing.");
            System.out.println("System.loadLibrary(" + name + ")");
            System.loadLibrary(name);
            return;
        } else {
            System.out.println("found.");
        }
        File f;
        try {
            f = File.createTempFile(filename + "-", suffix);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError(Utils.formatErr(e));
        }
        f.deleteOnExit();
        try (is) {
            byte[] buf = new byte[1024];
            try (FileOutputStream fos = new FileOutputStream(f)) {
                int n;
                while ((n = is.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
                fos.flush();
            }
        } catch (IOException e) {
            throw new UnsatisfiedLinkError(Utils.formatErr(e));
        }
        if (!f.setExecutable(true)) {
            throw new UnsatisfiedLinkError("failed setting executable on tmp file " + f.getAbsolutePath());
        }
        System.out.println("System.load(" + f.getAbsolutePath() + ")");
        System.load(f.getAbsolutePath());
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public static void validateVProxyVersion(String version) throws Exception {
        // version can be:
        // 1.2.3
        // 1.2.3-BETA-4
        // 1.2.3-BETA-4-DEV
        String majorMinorPatch = version;
        if (version.contains("-")) {
            if (version.startsWith("-")) {
                throw new Exception("invalid version, must not start with `-`: " + version);
            }
            if (version.endsWith("-")) {
                throw new Exception("invalid version, must not end with `-`: " + version);
            }
            var splitBySlash = version.split("-");
            if (splitBySlash.length != 3 && splitBySlash.length != 4) {
                throw new Exception("invalid version, invalid slash count: " + version);
            }
            var versionCat = splitBySlash[1];
            if (!versionCat.equals("ALPHA") && !versionCat.equals("BETA") && !versionCat.equals("RC")) {
                throw new Exception("invalid version, expecting ALPHA|BETA|RC, but got: " + versionCat);
            }
            var unstableNumberStr = splitBySlash[2];
            if (!Utils.isPositiveInteger(unstableNumberStr)) {
                throw new Exception("invalid version, expecting unstable version to be positive integer, but got: " + unstableNumberStr);
            }
            if (splitBySlash.length == 4) {
                if (!splitBySlash[3].equals("DEV")) {
                    throw new Exception("invalid version, expecting DEV tag, but got: " + splitBySlash[3]);
                }
            }
            majorMinorPatch = splitBySlash[0];
        }
        if (majorMinorPatch.startsWith(".")) {
            throw new Exception("invalid version, major.minor.patch must not start with `.`: " + majorMinorPatch);
        }
        if (majorMinorPatch.endsWith(".")) {
            throw new Exception("invalid version, major.minor.patch must not end with `.`: " + majorMinorPatch);
        }
        var split = majorMinorPatch.split("\\.");
        if (split.length != 3) {
            throw new Exception("invalid version, not major.minor.patch: " + majorMinorPatch);
        }
        var major = split[0];
        if (!Utils.isNonNegativeInteger(major)) {
            throw new Exception("invalid version, major version is not non-negative integer: " + major);
        }
        var minor = split[1];
        if (!Utils.isNonNegativeInteger(minor)) {
            throw new Exception("invalid version, minor version is not non-negative integer: " + minor);
        }
        var patch = split[2];
        if (!Utils.isNonNegativeInteger(patch)) {
            throw new Exception("invalid version, patch version is not non-negative integer: " + patch);
        }
    }

    public static int compareVProxyVersions(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }

        String[] mmpA = (a.contains("-") ? a.split("-")[0] : a).split("\\.");
        String[] mmpB = (b.contains("-") ? b.split("-")[0] : b).split("\\.");
        int[] mmpAN = new int[]{
            Integer.parseInt(mmpA[0]), Integer.parseInt(mmpA[1]), Integer.parseInt(mmpA[2])
        };
        int[] mmpBN = new int[]{
            Integer.parseInt(mmpB[0]), Integer.parseInt(mmpB[1]), Integer.parseInt(mmpB[2])
        };
        if (mmpAN[0] > mmpBN[0]) return 1;
        if (mmpAN[0] < mmpBN[0]) return -1;
        if (mmpAN[1] > mmpBN[1]) return 1;
        if (mmpAN[1] < mmpBN[1]) return -1;
        if (mmpAN[2] > mmpBN[2]) return 1;
        if (mmpAN[2] < mmpBN[2]) return -1;

        if (a.contains("-") && !b.contains("-")) return -1;
        if (b.contains("-") && !a.contains("-")) return 1;

        String[] splitA = a.split("-");
        String[] splitB = b.split("-");

        String catA = splitA[1];
        String catB = splitB[1];

        if (catA.equals("ALPHA") && !catB.equals("ALPHA")) return -1;
        if (catB.equals("ALPHA") && !catA.equals("ALPHA")) return 1;
        if (catA.equals("BETA") && catB.equals("RC")) return -1;
        if (catB.equals("BETA") && catA.equals("RC")) return 1;
        if (catA.equals("RC") && !catB.equals("RC")) return 1;
        if (catB.equals("RC") && !catA.equals("RC")) return -1;

        int unstableA = Integer.parseInt(splitA[2]);
        int unstableB = Integer.parseInt(splitB[2]);

        if (unstableA > unstableB) return 1;
        if (unstableB > unstableA) return -1;

        if (splitA.length == 3 && splitB.length == 4) return 1;
        if (splitB.length == 3 && splitA.length == 4) return -1;

        throw new Error("should not reach here: " + a + " <==> " + b);
    }

    private static final int[] maskValues = new int[33];

    static {
        for (var i = 1; i <= 32; ++i) {
            var res = 0;
            for (var j = 1; j <= i; ++j) {
                res = (1 << (32 - j)) | res;
            }
            maskValues[i] = res;
        }
    }

    public static int maskNumberToInt(int maskNumber) {
        if (maskNumber > 32) {
            throw new IllegalArgumentException("mask for ipv4 should be between [0,32], but got " + maskNumber);
        }
        if (maskNumber < 0) {
            throw new IllegalArgumentException("mask for ipv4 should be between [0,32], but got " + maskNumber);
        }
        return maskValues[maskNumber];
    }

    @SuppressWarnings({"ConcatenationWithEmptyString", "deprecation"})
    public static String formatTimestampForFileName(long ts) {
        Date d = new Date(ts);
        return "" +
               (d.getYear() + 1900) + "-" +
               fillToTen(d.getMonth() + 1) + "-" +
               fillToTen(d.getDate()) + "_" +
               fillToTen(d.getHours()) + "-" +
               fillToTen(d.getMinutes()) + "-" +
               fillToTen(d.getSeconds()) +
               "";
    }

    @SuppressWarnings({"ConcatenationWithEmptyString", "deprecation"})
    public static String formatTimestampForLogging(long ts) {
        Date d = new Date(ts);
        return "" +
               (d.getYear() + 1900) + "-" +
               fillToTen(d.getMonth() + 1) + "-" +
               fillToTen(d.getDate()) + " " +
               fillToTen(d.getHours()) + ":" +
               fillToTen(d.getMinutes()) + ":" +
               fillToTen(d.getSeconds()) + "." +
               fillToHundred((int) (ts % 1000)) +
               "";
    }

    private static String fillToTen(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    private static String fillToHundred(int n) {
        return (n < 10 ? "00" : (n < 100 ? "0" : "")) + n;
    }

    public static String escapePath(File path) {
        return escapePath(path.getAbsolutePath());
    }

    public static String escapePath(Path path) {
        return escapePath(path.toAbsolutePath().toString());
    }

    public static String escapePath(String path) {
        if (OS.isWindows()) {
            // no need to escape for windows, no `"` would appear in path
            return "\"" + path + "\"";
        }
        var escape = '\\';
        var chars = path.toCharArray();
        var sb = new StringBuilder("\"");
        for (var c : chars) {
            if (c == '"') {
                sb.append(escape);
            }
            sb.append(c);
        }
        sb.append('\"');
        return sb.toString();
    }
}
