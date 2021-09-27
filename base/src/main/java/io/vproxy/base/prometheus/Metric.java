package vproxy.base.prometheus;

import vjson.simple.SimpleString;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Metric {
    private static final AtomicLong indexes = new AtomicLong();
    final long index;
    public final String metric;
    public final Map<String, String> labels;
    final List<String> labelKeys;

    protected Metric(String metric, Map<String, String> labels) {
        index = indexes.incrementAndGet();
        this.metric = metric;
        Map<String, String> foo = new HashMap<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            foo.put(entry.getKey(), new SimpleString(entry.getValue()).stringify());
        }
        this.labels = Collections.unmodifiableMap(foo);
        labelKeys = new ArrayList<>(labels.keySet());
        labelKeys.sort(String::compareTo);
    }

    abstract public String type();

    abstract public String value();
}
