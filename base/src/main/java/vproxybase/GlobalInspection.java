package vproxybase;

import vjson.util.AppendableMap;
import vproxybase.prometheus.Counter;
import vproxybase.prometheus.Gauge;
import vproxybase.prometheus.Metric;
import vproxybase.prometheus.Metrics;
import vproxybase.util.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GlobalInspection {
    private static final GlobalInspection inst = new GlobalInspection();

    public static GlobalInspection getInstance() {
        return inst;
    }

    private final Metrics metrics = new Metrics();
    private final Gauge directBufferBytes;
    private final Counter directBufferAllocateCount;
    private final Counter directBufferFreeCount;

    private GlobalInspection() {
        Map<String, String> extraLabels = getExtraLabels();

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

        metrics.registerHelpMessage("direct_memory_bytes_current", "Current allocated direct memory in bytes");
        metrics.registerHelpMessage("direct_memory_allocate_count", "Total count of how many times the direct memory is allocated");
        metrics.registerHelpMessage("direct_memory_free_count", "Total count of how many times the direct memory is freed");
    }

    private Map<String, String> getExtraLabels() {
        // -DGlobalInspectionPrometheusLabels=key1=value1,key2=value2
        String labels = System.getProperty("GlobalInspectionPrometheusLabels");
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
        return ret;
    }

    public void addMetric(Metric m) {
        metrics.add(m);
    }

    public void removeMetric(Metric m) {
        metrics.remove(m);
    }

    public void directBufferAllocate(int size) {
        directBufferAllocateCount.incr(1);
        directBufferBytes.incr(size);
    }

    public void directBufferFree(int size) {
        directBufferFreeCount.incr(1);
        directBufferBytes.decr(size);
    }

    public String toPrometheusString() {
        return metrics.toString();
    }
}
