package vproxy.base;

import vjson.simple.SimpleString;
import vproxy.base.util.AppendableMap;
import vproxy.base.prometheus.Counter;
import vproxy.base.prometheus.Gauge;
import vproxy.base.prometheus.Metric;
import vproxy.base.prometheus.Metrics;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.FDInspection;
import vproxy.base.selector.wrap.VirtualFD;
import vproxy.base.util.*;
import vproxy.base.util.exception.NoException;
import vproxy.base.util.table.TR;
import vproxy.base.util.table.TableBuilder;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.FD;
import vproxy.vfd.NetworkFD;
import vproxy.vfd.ServerSocketFD;
import vproxy.vfd.SockAddr;
import vproxy.vfd.posix.PosixFD;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class GlobalInspection {
    private static final GlobalInspection inst = new GlobalInspection();

    public static GlobalInspection getInstance() {
        return inst;
    }

    private final Map<String, String> extraLabels;
    private final Metrics metrics = new Metrics();
    private final Gauge directBufferBytes;
    private final Counter directBufferAllocateCount;
    private final Counter directBufferFreeCount;
    private final Counter directBufferFinalizeBytesTotal;
    private final Counter directBufferFinalizeCount;
    private final Counter sslUnwrapTaskCount;
    private final Counter sslUnwrapTaskTimeMillisTotal;
    private final Gauge threadNumberCurrent;

    private final ConcurrentHashSet<SelectorEventLoop> runningLoops = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<VProxyThread> runningThreads = new ConcurrentHashSet<>();

    private GlobalInspection() {
        extraLabels = getExtraLabels();

        directBufferBytes = new Gauge("direct_memory_bytes_current", new AppendableMap<>()
            .append("type", "buffer")
            .appendAll(extraLabels));
        metrics.add(directBufferBytes);

        directBufferAllocateCount = new Counter("direct_memory_allocate_count", new AppendableMap<>()
            .append("type", "buffer")
            .appendAll(extraLabels));
        metrics.add(directBufferAllocateCount);

        directBufferFreeCount = new Counter("direct_memory_free_count", new AppendableMap<>()
            .append("type", "buffer")
            .appendAll(extraLabels));
        metrics.add(directBufferFreeCount);

        directBufferFinalizeBytesTotal = new Counter("direct_memory_finalize_bytes_total", new AppendableMap<>()
            .append("type", "buffer")
            .appendAll(extraLabels));
        metrics.add(directBufferFinalizeBytesTotal);

        directBufferFinalizeCount = new Counter("direct_memory_finalize_count", new AppendableMap<>()
            .append("type", "buffer")
            .appendAll(extraLabels));
        metrics.add(directBufferFinalizeCount);

        sslUnwrapTaskCount = new Counter("ssl_unwrap_task_count", new AppendableMap<>()
            .appendAll(extraLabels));
        metrics.add(sslUnwrapTaskCount);

        sslUnwrapTaskTimeMillisTotal = new Counter("ssl_unwrap_task_time_millis_total", new AppendableMap<>()
            .appendAll(extraLabels));
        metrics.add(sslUnwrapTaskTimeMillisTotal);

        threadNumberCurrent = new Gauge("thread_number_current", new AppendableMap<>()
            .appendAll(extraLabels));
        metrics.add(threadNumberCurrent);

        metrics.registerHelpMessage("direct_memory_bytes_current", "Current allocated direct memory in bytes");
        metrics.registerHelpMessage("direct_memory_allocate_count", "Total count of how many times the direct memory is allocated");
        metrics.registerHelpMessage("direct_memory_free_count", "Total count of how many times the direct memory is freed");
        metrics.registerHelpMessage("direct_memory_finalize_bytes_total", "Total bytes for finalized direct memory");
        metrics.registerHelpMessage("direct_memory_finalize_count", "Total count of how many times the direct memory is finalized");
        metrics.registerHelpMessage("ssl_unwrap_task_count", "Total count of how many times ssl unwrap requires executing a task");
        metrics.registerHelpMessage("ssl_unwrap_task_time_millis_total", "Total time cost for tasks required by ssl unwrapping");
        metrics.registerHelpMessage("thread_number_current", "The number of current running threads");
    }

    private Map<String, String> getExtraLabels() {
        // -DGlobalInspectionPrometheusLabels=key1=value1,key2=value2
        String labels = Utils.getSystemProperty("global_inspection_prometheus_labels");
        if (labels == null || labels.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, String> ret = new HashMap<>();
        String[] splitArray = labels.split(",");
        for (String split : splitArray) {
            if (split.isBlank()) {
                continue;
            }
            if (!split.contains("=")) {
                throw new IllegalArgumentException("invalid format, expecting k=v, but got " + split);
            }
            String key = split.substring(0, split.indexOf('='));
            String value = split.substring(split.indexOf('=') + 1);
            ret.put(key, value);
        }
        assert Logger.lowLevelDebug("reading GlobalInspectionPrometheusLabels=" + ret);
        return Collections.unmodifiableMap(ret);
    }

    public <M extends Metric> M addMetric(String metric, Map<String, String> labels, BiFunction<String, Map<String, String>, M> constructor) {
        Map<String, String> allLabels = new HashMap<>(labels.size() + extraLabels.size());
        allLabels.putAll(labels);
        allLabels.putAll(extraLabels);
        M m = constructor.apply(metric, allLabels);
        metrics.add(m);
        return m;
    }

    public void removeMetric(Metric m) {
        metrics.remove(m);
    }

    public void registerHelpMessage(String metric, String msg) {
        metrics.registerHelpMessage(metric, msg);
    }

    public void directBufferAllocate(int size) {
        directBufferAllocateCount.incr(1);
        directBufferBytes.incr(size);
    }

    public void directBufferFree(int size) {
        directBufferFreeCount.incr(1);
        directBufferBytes.decr(size);
    }

    public void directBufferFinalize(int size) {
        directBufferFinalizeCount.incr(1);
        directBufferFinalizeBytesTotal.incr(size);
    }

    public void sslUnwrapTask(long costMillis) {
        sslUnwrapTaskCount.incr(1);
        sslUnwrapTaskTimeMillisTotal.incr(costMillis);
    }

    public Runnable wrapThread(Runnable r) {
        return () -> {
            VProxyThread vt = (VProxyThread) Thread.currentThread();
            runningThreads.add(vt);
            threadNumberCurrent.incr(1);
            try {
                r.run();
            } finally {
                threadNumberCurrent.decr(1);
                runningThreads.remove(vt);
            }
        };
    }

    public void registerSelectorEventLoop(SelectorEventLoop loop) {
        runningLoops.add(loop);
    }

    public void deregisterSelectorEventLoop(SelectorEventLoop loop) {
        runningLoops.remove(loop);
    }

    public String getPrometheusString() {
        return metrics.toString();
    }

    public String getStackTraces() {
        var threads = new HashSet<>(runningThreads);
        StringBuilder sb = new StringBuilder();
        for (var vt : threads) {
            var name = new SimpleString(vt.thread().getName()).stringify();
            var tid = vt.thread().getId();
            var state = vt.thread().getState().name();
            sb.append(name).append(" ").append("tid=").append(tid).append("\n")
                .append("   java.lang.Thread.State: ").append(state).append("\n");
            var stacks = vt.thread().getStackTrace();
            if (stacks.length != 0) {
                for (var s : stacks) {
                    sb.append("\tat ").append(s).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public void getOpenFDs(Consumer<String> cb) {
        var loops = new HashSet<>(runningLoops);
        TableBuilder table = new TableBuilder();
        var tr = table.tr();
        tr.td("TID").td("WATCHED").td("FIRED").td("FD").td("VIRTUAL").td("LOCAL").td("REMOTE").td("REAL").td("REAL-VIRTUAL").td("REAL-LOCAL").td("REAL-REMOTE");
        var ite = loops.iterator();
        var callback = new RunOnLoopCallback<>(new Callback<String, NoException>() {
            @Override
            protected void onSucceeded(String value) {
                cb.accept(value);
            }

            @Override
            protected void onFailed(NoException err) {
            }
        });
        recursiveGetOpenFds(ite, table, callback::succeeded);
    }

    private void recursiveGetOpenFds(Iterator<SelectorEventLoop> ite, TableBuilder table, Consumer<String> cb) {
        if (!ite.hasNext()) {
            cb.accept(table.toString());
            return;
        }
        var loop = ite.next();
        var thread = loop.getRunningThread();
        if (thread == null) { // not running
            recursiveGetOpenFds(ite, table, cb);
            return;
        }
        var set = new HashSet<FDInspection>();
        loop.copyChannels(set, () -> {
            for (var f : set) {
                var tr = table.tr();
                tr.td(thread.getId() + "");
                var fd = f.fd;
                tr.td(f.watchedEvents.toString());
                tr.td(f.firedEvents == null ? "-" : f.firedEvents.toString());
                appendFDType(tr, fd);
                tr.td(fd instanceof VirtualFD ? "Y" : "N");
                appendLocalRemote(tr, fd);
                FD real = null;
                try {
                    real = fd.real();
                } catch (Throwable ignore) {
                }
                if (real != null && real != fd) {
                    appendFDType(tr, real);
                    tr.td(real instanceof VirtualFD ? "Y" : "N");
                    appendLocalRemote(tr, real);
                } else {
                    tr.td("-").td("-").td("-").td("-");
                }
            }
            recursiveGetOpenFds(ite, table, cb);
        });
    }

    private void appendFDType(TR tr, FD fd) {
        if (fd instanceof PosixFD) {
            tr.td(fd.getClass().getSimpleName() + ":" + ((PosixFD) fd).getFD());
        } else {
            tr.td(fd.getClass().getSimpleName());
        }
    }

    private void appendAddress(TR tr, SockAddr sockAddr) {
        String s = sockAddr.toString();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        tr.td(s);
    }

    private void appendLocalRemote(TR tr, FD fd) {
        if (fd instanceof NetworkFD) {
            SockAddr local = null;
            SockAddr remote = null;
            try {
                local = ((NetworkFD) fd).getLocalAddress();
            } catch (Throwable ignore) {
            }
            try {
                remote = ((NetworkFD) fd).getRemoteAddress();
            } catch (Throwable ignore) {
            }
            if (local == null) {
                tr.td("-");
            } else {
                appendAddress(tr, local);
            }
            if (remote == null) {
                tr.td("-");
            } else {
                appendAddress(tr, remote);
            }
        } else if (fd instanceof ServerSocketFD) {
            SockAddr local = null;
            try {
                local = ((ServerSocketFD) fd).getLocalAddress();
            } catch (Throwable ignore) {
            }
            if (local != null) {
                appendAddress(tr, local);
            }
        } else {
            tr.td("-").td("-");
        }
    }
}
