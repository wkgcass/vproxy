package vproxy.base.prometheus;

import vproxy.base.util.coll.ConcurrentHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Metrics {
    private final Set<Metric> metrics = new ConcurrentHashSet<>();
    private final Map<String, String> helpMessages = new ConcurrentHashMap<>();

    public Metrics() {
    }

    public void add(Metric metric) {
        metrics.add(metric);
    }

    public void remove(Metric metric) {
        metrics.remove(metric);
    }

    public void registerHelpMessage(String metric, String message) {
        helpMessages.put(metric, message);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<Metric> metrics = new ArrayList<>(this.metrics);
        metrics.sort((a, b) -> {
            int ret = a.metric.compareTo(b.metric);
            if (ret == 0) {
                return (int) (a.index - b.index);
            } else {
                return ret;
            }
        });

        String lastMetricName = null;
        for (Metric metric : metrics) {
            if (!metric.metric.equals(lastMetricName)) {
                if (helpMessages.containsKey(metric.metric)) {
                    sb.append("# HELP ").append(metric.metric).append(" ").append(helpMessages.get(metric.metric)).append("\n");
                }
                sb.append("# TYPE ").append(metric.metric).append(" ").append(metric.type()).append("\n");
            }
            lastMetricName = metric.metric;
            sb.append(metric.metric).append("{");
            {
                boolean isFirst = true;
                for (String key : metric.labelKeys) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        sb.append(",");
                    }
                    // the value is already formatted (quoted)
                    sb.append(key).append("=").append(metric.labels.get(key));
                }
            }
            sb.append("}").append(" ").append(metric.value()).append("\n");
        }
        return sb.toString();
    }
}
