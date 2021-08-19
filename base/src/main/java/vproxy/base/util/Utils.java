package vproxy.base.util;

import vproxy.base.util.callback.BlockCallback;
import vproxy.base.util.callback.Callback;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.exception.XException;
import vproxy.base.util.thread.VProxyThread;
import vproxy.base.util.unsafe.JDKUnsafe;
import vproxy.vfd.FDProvider;
import vproxy.vpacket.Ipv4Packet;
import vproxy.vpacket.Ipv6Packet;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        if (err instanceof RuntimeException) {
            return base + Arrays.asList(err.getStackTrace());
        } else {
            return base;
        }
    }

    public static <T> String formatArrayToStringCompact(T[] arr) {
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
        Logger.alert("trying to execute script:\n" + script);
        File file = File.createTempFile("script", ".sh");
        try {
            Files.writeString(file.toPath(), script);
            if (!file.setExecutable(true)) {
                throw new Exception("setting executable to script " + file.getAbsolutePath() + " failed");
            }
            ProcessBuilder pb = new ProcessBuilder(file.getAbsolutePath());
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

    public static void writeFileWithBackup(String filepath, String content) throws Exception {
        Logger.alert("Trying to write into file: " + filepath + ".new");
        File f = new File(filepath + ".new");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (!f.createNewFile()) {
            throw new Exception("Create new file " + filepath + ".new failed");
        }
        try (FileOutputStream fos = new FileOutputStream(f)) {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
            bw.write(content);
            bw.flush();
        }

        Logger.alert("Backup old file " + filepath);
        backupAndRemove(filepath);

        Logger.alert("Move new file to " + filepath);
        Files.move(Path.of(f.getAbsolutePath()), Path.of(filepath), StandardCopyOption.REPLACE_EXISTING);

        Logger.alert("Writing into file done: " + filepath);
    }

    private static void backupAndRemove(String filepath) throws Exception {
        File f = new File(filepath);
        File bakF = new File(filepath + ".bak");

        if (!f.exists())
            return; // do nothing if no need to backup
        if (bakF.exists() && !bakF.delete()) // remove old backup file
            throw new Exception("remove old backup file failed: " + bakF.getPath());
        if (f.exists() && !f.renameTo(bakF)) // do rename (backup)
            throw new Exception("backup the file failed: " + bakF.getPath());
    }

    public static String writeTemporaryFile(String prefix, String suffix, byte[] content) throws IOException {
        return writeTemporaryFile(prefix, suffix, content, false);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static String writeTemporaryFile(String prefix, String suffix, byte[] content, boolean executable) throws IOException {
        File f = File.createTempFile("vproxy-" + ProcessHandle.current().pid() + "-" + prefix, "." + suffix);
        f.deleteOnExit();
        Files.write(f.toPath(), content);
        f.setReadable(true);
        if (executable) {
            f.setExecutable(true);
        }
        return f.getAbsolutePath();
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

    public static String getSystemProperty(String key) {
        return getSystemProperty(key, null);
    }

    public static String getSystemProperty(String pattern, String defaultValue) {
        String[] split = pattern.split("_");
        Set<String> results = new HashSet<>();

        String ret;

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
        if (results.size() > 1)
            throw new IllegalStateException(
                "multiple keys with different patterns set for the same property");
        if (results.isEmpty()) {
            return null;
        }
        return results.iterator().next();
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
}
