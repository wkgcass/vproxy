package net.cassite.vproxy.component.check;

public class HealthCheckConfig {
    public final int timeout;
    public final int period;
    public final int up;
    public final int down;

    public HealthCheckConfig(int timeout, int period, int up, int down) {
        this.timeout = timeout;
        this.period = period;
        this.up = up;
        this.down = down;
    }

    public HealthCheckConfig(HealthCheckConfig c) {
        this(c.timeout, c.period, c.up, c.down);
    }

    @Override
    public String toString() {
        return "HealthCheckConfig{" +
            "timeout=" + timeout +
            ", period=" + period +
            ", up=" + up +
            ", down=" + down +
            '}';
    }
}
