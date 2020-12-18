package vproxybase.prometheus;

import java.util.Map;
import java.util.function.Supplier;

public class GaugeF extends Metric {
    private final Supplier<Long> dataFunc;

    public GaugeF(String msg, Map<String, String> labels, Supplier<Long> dataFunc) {
        super(msg, labels);
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
