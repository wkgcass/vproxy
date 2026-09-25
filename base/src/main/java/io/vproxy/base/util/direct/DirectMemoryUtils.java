package io.vproxy.base.util.direct;

import io.vproxy.base.GlobalInspection;
import io.vproxy.base.prometheus.Counter;
import io.vproxy.base.prometheus.GaugeF;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.objectpool.ConcurrentObjectPool;
import io.vproxy.base.util.unsafe.SunUnsafe;

import java.nio.ByteBuffer;
import java.util.Map;

public class DirectMemoryUtils {
    private DirectMemoryUtils() {
    }

    // iOS hosts run under a tight process memory cap where
    // the cache is fatal - allocate and immediately free there
    private static final boolean POOL_DISABLED = OS.isIOS();

    private static final int BUF_POOL_SIZE = 128;

    // iOS hosts run under a tight process memory cap: do not even
    // instantiate the pools there
    private static ConcurrentObjectPool<DirectByteBuffer> pool() {
        if (OS.isIOS()) {
            return null;
        }
        return new ConcurrentObjectPool<>(BUF_POOL_SIZE);
    }
    private static long poolSize(ConcurrentObjectPool<DirectByteBuffer> pool) {
        return pool == null ? 0 : pool.size();
    }
    private static final ConcurrentObjectPool<DirectByteBuffer> _1 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _2 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _4 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _8 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _16 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _32 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _64 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _128 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _256 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _512 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _1024 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _2048 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _4096 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _8192 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _16384 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _24576 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _32768 = pool();
    private static final ConcurrentObjectPool<DirectByteBuffer> _65536 = pool();

    private static final Counter directMemoryCacheMissCount = GlobalInspection.getInstance().addMetric(
        "direct_memory_cache_miss_count_total",
        Map.of("type", "buffer"),
        Counter::new);
    private static final Counter directMemoryCacheHitCount = GlobalInspection.getInstance().addMetric(
        "direct_memory_cache_hit_count_total",
        Map.of("type", "buffer"),
        Counter::new);
    private static final Counter directMemoryCacheFailedStoringCount = GlobalInspection.getInstance().addMetric(
        "direct_memory_cache_failed_storing_count_total",
        Map.of("type", "buffer"),
        Counter::new);
    private static final Counter directMemoryCacheStoredCount = GlobalInspection.getInstance().addMetric(
        "direct_memory_cache_stored_count_total",
        Map.of("type", "buffer"),
        Counter::new);

    static {
        GlobalInspection.getInstance().registerHelpMessage(
            "direct_memory_cache_miss_count_total",
            "Total cache miss of direct memory"
        );
        GlobalInspection.getInstance().registerHelpMessage(
            "direct_memory_cache_hit_count_total",
            "Total cache hit of direct memory"
        );
        GlobalInspection.getInstance().registerHelpMessage(
            "direct_memory_cache_failed_storing_count_total",
            "Total failed storing direct memory cache times"
        );
        GlobalInspection.getInstance().registerHelpMessage(
            "direct_memory_cache_stored_count_total",
            "Total stored direct memory cache times"
        );
        GlobalInspection.getInstance().registerHelpMessage(
            "cached_direct_memory_count_current",
            "Current cached direct memory in bytes");
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "1"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_1))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "2"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_2))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "4"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_4))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "8"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_8))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "16"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_16))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "32"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_32))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "64"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_64))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "128"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_128))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "256"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_256))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "512"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_512))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "1024"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_1024))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "2048"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_2048))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "4096"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_4096))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "8192"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_8192))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "16384"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_16384))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "24576"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_24576))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "32768"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_32768))
        );
        GlobalInspection.getInstance().addMetric(
            "cached_direct_memory_count_current",
            Map.of("type", "buffer", "size_in_bytes", "65536"),
            (s, m) -> new GaugeF(s, m, () -> (long) poolSize(_65536))
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

    private static DirectByteBuffer getBufferCache(ConcurrentObjectPool<DirectByteBuffer> buffers) {
        if (buffers == null) {
            return null;
        }
        DirectByteBuffer buf = buffers.poll();
        if (buf == null) {
            directMemoryCacheMissCount.incr(1);
        } else {
            directMemoryCacheHitCount.incr(1);
        }
        return buf;
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

    private static boolean releaseBufferCache(ConcurrentObjectPool<DirectByteBuffer> buffers, DirectByteBuffer buf) {
        if (buffers == null) {
            return false;
        }
        boolean ret = buffers.add(buf);
        if (ret) {
            directMemoryCacheStoredCount.incr(1);
        } else {
            directMemoryCacheFailedStoringCount.incr(1);
        }
        return ret;
    }

    public static DirectByteBuffer allocateDirectBuffer(int size) {
        DirectByteBuffer directByteBuffer = getBufferCache(size);
        if (!POOL_DISABLED && directByteBuffer != null) {
            assert Logger.lowLevelDebug("cached direct buffer retrieved: " + size);
            directByteBuffer.limit(directByteBuffer.capacity()).position(0);
            return directByteBuffer;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(size);
        GlobalInspection.getInstance().directBufferAllocate(buf.capacity());
        return new DirectByteBuffer(buf);
    }

    static boolean free(DirectByteBuffer buffer, boolean tryCache) {
        assert Logger.lowLevelDebug("run DirectMemoryUtils.free");
        if (!buffer.realBuffer().isDirect()) {
            assert Logger.lowLevelDebug("not direct buffer");
            return true; // return true because it does not need cleaning
        }
        if (tryCache && !POOL_DISABLED) {
            boolean succeeded = releaseBufferCache(buffer);
            if (succeeded) {
                assert Logger.lowLevelDebug("direct buffer cached: " + buffer.capacity());
                return false;
            }
        }
        assert Logger.lowLevelDebug("is direct buffer, do clean");
        GlobalInspection.getInstance().directBufferFree(buffer.capacity());
        SunUnsafe.invokeCleaner(buffer.realBuffer());
        return true;
    }
}
