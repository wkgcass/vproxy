package io.vproxy.base.component.check;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.TimerEvent;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.vfd.IPPort;

import java.io.IOException;

public class HealthCheckClient {
    class ConnectResultHandler {
        void onSucceeded(ConnectResult result) {
            handler.upOnce(connectClient.remote, result);
            if (currentDown > 0) {
                // decrease down count if it's not zero
                --currentDown;
                assert Logger.lowLevelDebug("DOWN: " + currentDown + "/" + down);
            } else {
                if (nowIsUp) {
                    // now is up, so no need to increase the up counter
                    return;
                }
                if (currentUp == up - 1) {
                    // should trigger up event
                    nowIsUp = true;
                    handler.up(connectClient.remote);
                    currentUp = 0;
                    return;
                }
                // increase the up counter
                ++currentUp;
                assert Logger.lowLevelDebug("TO-UP: " + currentUp + "/" + up);
            }
        }

        void onFailed(String reason) {
            handler.downOnce(connectClient.remote, reason);
            if (currentUp > 0) {
                // decrease up count if it's not zero
                --currentUp;
                assert Logger.lowLevelDebug("UP: " + currentUp + "/" + up);
            } else {
                if (!nowIsUp) {
                    // now is down, so no need to increase the currentDown counter
                    return;
                }
                if (currentDown == down - 1) {
                    // should trigger down event
                    nowIsUp = false;
                    handler.down(connectClient.remote, reason);
                    currentDown = 0;
                    return;
                }
                // increase the down counter
                ++currentDown;
                assert Logger.lowLevelDebug("TO-DOWN: " + currentDown + "/" + down);
            }
        }
    }

    public final ConnectClient connectClient;
    public final int period;
    public final int up;
    public final int down;
    private final HealthCheckHandler handler;
    private final ConnectResultHandler connectResultHandler = new ConnectResultHandler();

    private int currentUp = 0;
    private int currentDown = 0;
    private boolean nowIsUp;

    private TimerEvent periodTimer;

    private boolean stopped = true;

    public HealthCheckClient(NetEventLoop eventLoop,
                             IPPort remote,
                             HealthCheckConfig healthCheckConfig,
                             AnnotatedHcConfig annotatedHcConfig,
                             boolean initialIsUp,
                             HealthCheckHandler handler) {
        this.connectClient = new ConnectClient(
            eventLoop, remote,
            healthCheckConfig.checkProtocol,
            healthCheckConfig.timeout,
            annotatedHcConfig);

        this.period = healthCheckConfig.period;
        this.up = healthCheckConfig.up;
        this.down = healthCheckConfig.down;
        nowIsUp = initialIsUp;
        this.handler = handler;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void start() {
        if (!stopped)
            return;
        stopped = false;
        doCheck(this::periodic);
    }

    private void doCheck(Runnable cb) {
        if (stopped) {
            doStop();
            return;
        }

        connectClient.handle(new Callback<>() {
            @Override
            protected void onSucceeded(ConnectResult result) {
                connectResultHandler.onSucceeded(result);
                cb.run();
            }

            @Override
            protected void onFailed(IOException err) {
                String reason = err.getClass().getSimpleName();
                String msg = err.getMessage();
                if (msg != null && !msg.isBlank()) {
                    reason = reason + ": " + msg;
                }
                connectResultHandler.onFailed(reason);
                cb.run();
            }
        });
    }

    private void periodic() {
        periodTimer = connectClient.eventLoop.getSelectorEventLoop().delay(period, () -> doCheck(this::periodic));
    }

    public void stop() {
        if (stopped)
            return;
        doStop();
    }

    private void doStop() {
        stopped = true;
        if (periodTimer != null) {
            periodTimer.cancel();
        }
        connectClient.stop();
        periodTimer = null;
    }

    // call this method
    // and the down count will +1
    public void manuallyDownOnce() {
        if (stopped)
            return; // ignore if already stopped
        // should run on event loop thread
        // because the callback not thread safe
        connectClient.eventLoop.getSelectorEventLoop().runOnLoop(
            () -> connectResultHandler.onFailed("passive down")
        );
    }
}
