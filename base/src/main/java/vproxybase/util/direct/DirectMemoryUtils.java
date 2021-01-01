package vproxybase.util.direct;

import sun.misc.Unsafe;
import vproxybase.GlobalInspection;
import vproxybase.prometheus.GaugeF;
import vproxybase.util.Logger;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DirectMemoryUtils {
    private DirectMemoryUtils() {
    }

    private static final Unsafe U;

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

    private static final int BUF_POOL_SIZE = 128;
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _1 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _2 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _4 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _8 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _16 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _32 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _64 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _128 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _256 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _512 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _1024 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _2048 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _4096 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _8192 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _16384 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _24576 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _32768 = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DirectByteBuffer> _65536 = new ConcurrentLinkedQueue<>();

    static {
        GlobalInspection.getInstance().registerHelpMessage(
            "cached_direct_memory_count_current",
            "Current cached direct memory in bytes");
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "1"),
            (s, m) -> new GaugeF(s, m, () -> (long) _1.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "2"),
            (s, m) -> new GaugeF(s, m, () -> (long) _2.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "4"),
            (s, m) -> new GaugeF(s, m, () -> (long) _4.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "8"),
            (s, m) -> new GaugeF(s, m, () -> (long) _8.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "16"),
            (s, m) -> new GaugeF(s, m, () -> (long) _16.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "32"),
            (s, m) -> new GaugeF(s, m, () -> (long) _32.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "64"),
            (s, m) -> new GaugeF(s, m, () -> (long) _64.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "128"),
            (s, m) -> new GaugeF(s, m, () -> (long) _128.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "256"),
            (s, m) -> new GaugeF(s, m, () -> (long) _256.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "512"),
            (s, m) -> new GaugeF(s, m, () -> (long) _512.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "1024"),
            (s, m) -> new GaugeF(s, m, () -> (long) _1024.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "2048"),
            (s, m) -> new GaugeF(s, m, () -> (long) _2048.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "4096"),
            (s, m) -> new GaugeF(s, m, () -> (long) _4096.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "8192"),
            (s, m) -> new GaugeF(s, m, () -> (long) _8192.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "16384"),
            (s, m) -> new GaugeF(s, m, () -> (long) _16384.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "24576"),
            (s, m) -> new GaugeF(s, m, () -> (long) _24576.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "32768"),
            (s, m) -> new GaugeF(s, m, () -> (long) _32768.size())
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "65536"),
            (s, m) -> new GaugeF(s, m, () -> (long) _65536.size())
        );
    }

    private static DirectByteBuffer getBufferCache(int size) {
        switch (size) {
            case 1:
                return getBufferCache(_1);
            case 2:
                return getBufferCache(_2);
            case 4:
                return getBufferCache(_4);
            case 8:
                return getBufferCache(_8);
            case 16:
                return getBufferCache(_16);
            case 32:
                return getBufferCache(_32);
            case 64:
                return getBufferCache(_64);
            case 128:
                return getBufferCache(_128);
            case 256:
                return getBufferCache(_256);
            case 512:
                return getBufferCache(_512);
            case 1024:
                return getBufferCache(_1024);
            case 2048:
                return getBufferCache(_2048);
            case 4096:
                return getBufferCache(_4096);
            case 8192:
                return getBufferCache(_8192);
            case 16384:
                return getBufferCache(_16384);
            case 24576:
                return getBufferCache(_24576);
            case 32768:
                return getBufferCache(_32768);
            case 65536:
                return getBufferCache(_65536);
            default:
                return null;
        }
    }

    private static DirectByteBuffer getBufferCache(ConcurrentLinkedQueue<DirectByteBuffer> buffers) {
        return buffers.poll();
    }

    private static boolean releaseBufferCache(DirectByteBuffer buf) {
        switch (buf.capacity()) {
            case 1:
                return releaseBufferCache(_1, buf);
            case 2:
                return releaseBufferCache(_2, buf);
            case 4:
                return releaseBufferCache(_4, buf);
            case 8:
                return releaseBufferCache(_8, buf);
            case 16:
                return releaseBufferCache(_16, buf);
            case 32:
                return releaseBufferCache(_32, buf);
            case 64:
                return releaseBufferCache(_64, buf);
            case 128:
                return releaseBufferCache(_128, buf);
            case 256:
                return releaseBufferCache(_256, buf);
            case 512:
                return releaseBufferCache(_512, buf);
            case 1024:
                return releaseBufferCache(_1024, buf);
            case 2048:
                return releaseBufferCache(_2048, buf);
            case 4096:
                return releaseBufferCache(_4096, buf);
            case 8192:
                return releaseBufferCache(_8192, buf);
            case 16384:
                return releaseBufferCache(_16384, buf);
            case 24576:
                return releaseBufferCache(_24576, buf);
            case 32768:
                return releaseBufferCache(_32768, buf);
            case 65536:
                return releaseBufferCache(_65536, buf);
            default:
                return false;
        }
    }

    private static boolean releaseBufferCache(ConcurrentLinkedQueue<DirectByteBuffer> buffers, DirectByteBuffer buf) {
        if (buffers.size() >= BUF_POOL_SIZE) {
            return false;
        }
        buffers.add(buf);
        return true;
    }

    public static DirectByteBuffer allocateDirectBuffer(int size) {
        DirectByteBuffer directByteBuffer = getBufferCache(size);
        if (directByteBuffer != null) {
            assert Logger.lowLevelDebug("cached direct buffer retrieved: " + size);
            directByteBuffer.limit(directByteBuffer.capacity()).position(0);
            return directByteBuffer;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(size);
        GlobalInspection.getInstance().directBufferAllocate(buf.capacity());
        return new DirectByteBuffer(buf);
    }

    static boolean free(DirectByteBuffer buffer, boolean tryCache) {
        assert Logger.lowLevelDebug("run Utils.clean");
        if (!buffer.realBuffer().isDirect()) {
            assert Logger.lowLevelDebug("not direct buffer");
            return true; // return true because it does not need cleaning
        }
        if (tryCache) {
            boolean succeeded = releaseBufferCache(buffer);
            if (succeeded) {
                assert Logger.lowLevelDebug("direct buffer cached: " + buffer.capacity());
                return false;
            }
        }
        assert Logger.lowLevelDebug("is direct buffer, do clean");
        U.invokeCleaner(buffer.realBuffer());
        GlobalInspection.getInstance().directBufferFree(buffer.capacity());
        return true;
    }
}
