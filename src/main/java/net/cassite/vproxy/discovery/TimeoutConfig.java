package net.cassite.vproxy.discovery;

import net.cassite.vproxy.component.check.CheckProtocol;
import net.cassite.vproxy.component.check.HealthCheckConfig;

public class TimeoutConfig {
    public final int ppsLimitWhenNotJoined;// = 250; // when nodes.size() == 0, send udp packet in this speed
    public final int intervalWhenNotJoined;// = 10500; // when nodes.size() == 0, the interval between two searches, bigger than hc.period * hc.up
    public final int ppsLimitWhenJoined;// = 20; // when nodes.size() <> 0, send udp packet in this speed
    public final int intervalWhenJoined;// = 60000; // when nodes.size() <> 0, the interval between two searches
    public final int detachTimeout;// = 5 * 60 * 1000; // if a node is unhealthy for a long time, do detach it

    final int delayWhenNotJoined;// = 1000 / ppsLimitWhenNotJoined;
    final int delayWhenJoined;// = 1000 / ppsLimitWhenJoined;

    public TimeoutConfig(int ppsLimitWhenNotJoined,
                         int intervalWhenNotJoined,
                         int ppsLimitWhenJoined,
                         int intervalWhenJoined,
                         int detachTimeout) {
        this.ppsLimitWhenNotJoined = ppsLimitWhenNotJoined;
        this.intervalWhenNotJoined = intervalWhenNotJoined;
        this.ppsLimitWhenJoined = ppsLimitWhenJoined;
        this.intervalWhenJoined = intervalWhenJoined;
        this.detachTimeout = detachTimeout;

        this.delayWhenNotJoined = 1000 / ppsLimitWhenNotJoined;
        this.delayWhenJoined = 1000 / ppsLimitWhenJoined;
    }

    public static TimeoutConfig getDefault() {
        return new TimeoutConfig(
            250,
            10500,
            50,
            60 * 1000,
            5 * 60 * 1000);
    }

    public static HealthCheckConfig getDefaultHc() {
        return new HealthCheckConfig(500, 5000, 2, 3, CheckProtocol.tcpDelay);
    }
}
