package vproxy.base.selector.wrap.kcp.mock;

public class InternalLoggerFactory {
    public static InternalLogger getInstance(@SuppressWarnings("unused") String x) {
        return new InternalLogger();
    }

    public static InternalLogger getInstance(@SuppressWarnings("unused") Class x) {
        return new InternalLogger();
    }
}
