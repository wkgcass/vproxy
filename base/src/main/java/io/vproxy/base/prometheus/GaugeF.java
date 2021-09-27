package io.vproxy.base.prometheus;

import java.util.Map;
import java.util.function.Supplier;

public class GaugeF extends Metric {
    private final Supplier<Long> dataFunc;

    public GaugeF(String metric, Map<String, String> labels, Supplier<Long> dataFunc) {
        super(metric, labels);
        this.dataFunc = dataFunc;
    }

    @Override
    public String type() {
        return "gauge";
    }

    @Override
    public String value() {
        return dataFunc.get() + "";
    }
}
