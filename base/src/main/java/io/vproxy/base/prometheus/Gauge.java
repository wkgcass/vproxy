package io.vproxy.base.prometheus;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class Gauge extends Metric {
    private final LongAdder adder = new LongAdder();

    public Gauge(String metric, Map<String, String> labels) {
        super(metric, labels);
    }

    @Override
    public String type() {
        return "gauge";
    }

    @Override
    public String value() {
        return "" + longValue();
    }

    public void incr(long n) {
        adder.add(n);
    }

    public void decr(long n) {
        adder.add(-n);
    }

    public long longValue() {
        return adder.longValue();
    }

    public void clear() {
        adder.reset();
    }
}
