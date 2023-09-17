package io.vproxy.base.component.check;

public class HealthCheckConfig {
    public final int timeout;
    public final int period;
    public final int up;
    public final int down;
    public final CheckProtocol checkProtocol;

    public HealthCheckConfig(int timeout, int period, int up, int down) {
        this(timeout, period, up, down, CheckProtocol.tcp);
    }

    public HealthCheckConfig(int timeout, int period, int up, int down, CheckProtocol checkProtocol) {
        this.timeout = timeout;
        this.period = period;
        this.up = up;
        this.down = down;
        this.checkProtocol = checkProtocol;
    }

    public HealthCheckConfig(HealthCheckConfig c) {
        this(c.timeout, c.period, c.up, c.down, c.checkProtocol);
    }

    public static HealthCheckConfig ofTcpDefault() {
        return new HealthCheckConfig(2000, 5000, 2, 3);
    }

    public static HealthCheckConfig ofNone() {
        return new HealthCheckConfig(2000, 5000, 1, 1, CheckProtocol.none);
    }

    @Override
    public String toString() {
        return "HealthCheckConfig{" +
            "timeout=" + timeout +
            ", period=" + period +
            ", up=" + up +
            ", down=" + down +
            ", checkProtocol=\"" + checkProtocol + '\"' +
            '}';
    }
}
