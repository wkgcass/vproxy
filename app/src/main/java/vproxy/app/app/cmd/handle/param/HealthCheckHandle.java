package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.component.check.CheckProtocol;
import vproxy.base.component.check.HealthCheckConfig;
import vproxy.base.util.exception.XException;

public class HealthCheckHandle {
    private HealthCheckHandle() {
    }

    public static HealthCheckConfig getHealthCheckConfig(Command cmd) throws Exception {
        int timeout = Integer.parseInt(cmd.args.get(Param.timeout));
        int period = Integer.parseInt(cmd.args.get(Param.period));
        int up = Integer.parseInt(cmd.args.get(Param.up));
        int down = Integer.parseInt(cmd.args.get(Param.down));
        CheckProtocol protocol;
        if (cmd.args.containsKey(Param.protocol)) {
            try {
                protocol = CheckProtocol.valueOf(cmd.args.get(Param.protocol));
            } catch (IllegalArgumentException e) {
                throw new XException("invalid health check config");
            }
        } else {
            protocol = CheckProtocol.tcp;
        }

        if (timeout < 0 || period < 0 || up < 0 || down < 0)
            throw new XException("invalid health check config");
        return new HealthCheckConfig(timeout, period, up, down, protocol);
    }
}
