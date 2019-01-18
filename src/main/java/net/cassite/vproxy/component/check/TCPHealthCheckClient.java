package net.cassite.vproxy.component.check;

import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.selector.TimerEvent;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Logger;

import java.io.IOException;
import java.net.SocketAddress;

public class TCPHealthCheckClient {
    class ConnectResultHandler {
        void onSucceeded() {
            handler.upOnce(connectClient.remote);
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

        void onFailed() {
            handler.downOnce(connectClient.remote);
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
                    handler.down(connectClient.remote);
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

    public TCPHealthCheckClient(NetEventLoop eventLoop,
                                SocketAddress remote,
                                int timeout,
                                int period,
                                int up, int down,
                                boolean initialIsUp,
                                HealthCheckHandler handler) {
        this.connectClient = new ConnectClient(eventLoop, remote, timeout);
        this.period = period;
        this.up = up;
        this.down = down;
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

        connectClient.handle(new Callback<Void, IOException>() {
            @Override
            protected void onSucceeded(Void value) {
                connectResultHandler.onSucceeded();
                cb.run();
            }

            @Override
            protected void onFailed(IOException err) {
                connectResultHandler.onFailed();
                cb.run();
            }
        });
    }

    private void periodic() {
        periodTimer = connectClient.eventLoop.selectorEventLoop.delay(period, () -> doCheck(this::periodic));
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
        periodTimer = null;
    }
}
