package vswitch;

import vfd.DatagramFD;
import vfd.EventSet;
import vfd.FDProvider;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.ByteArray;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Timer;
import vproxy.util.crypto.Aes256Key;
import vswitch.packet.VProxySwitchPacket;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class VXLanAdaptorHandlers {
    private static final int PING_PERIOD = 20 * 1000;

    private VXLanAdaptorHandlers() {
    }

    public static void launchGeneralAdaptor(SelectorEventLoop loop, InetSocketAddress switchSockAddr, InetSocketAddress vxlanSockAddr, InetSocketAddress listenAddr, String password) throws IOException {
        DatagramFD switchSock = null;
        DatagramFD vxlanSock = null;
        DatagramFD listenSock = null;

        try {
            switchSock = FDProvider.get().openDatagramFD();
            switchSock.configureBlocking(false);
            switchSock.connect(switchSockAddr);

            vxlanSock = FDProvider.get().openDatagramFD();
            vxlanSock.configureBlocking(false);
            vxlanSock.connect(vxlanSockAddr);

            listenSock = FDProvider.get().openDatagramFD();
            listenSock.configureBlocking(false);
            listenSock.bind(listenAddr);

            launchGeneralAdaptor(loop, switchSock, vxlanSock, listenSock, password);
        } catch (IOException e) {
            if (switchSock != null) {
                try {
                    switchSock.close();
                } catch (IOException e1) {
                    Logger.shouldNotHappen("closing udp sock " + switchSock + " failed", e1);
                }
            }
            if (vxlanSock != null) {
                try {
                    vxlanSock.close();
                } catch (IOException e1) {
                    Logger.shouldNotHappen("closing udp sock " + switchSock + " failed", e1);
                }
            }
            if (listenSock != null) {
                try {
                    listenSock.close();
                } catch (IOException e1) {
                    Logger.shouldNotHappen("closing udp sock " + switchSock + " failed", e1);
                }
            }
            throw e;
        }
    }

    public static void launchGeneralAdaptor(SelectorEventLoop loop, DatagramFD switchSock, DatagramFD vxlanSock, DatagramFD listenSock, String password) throws IOException {
        loop.add(listenSock, EventSet.read(), null, new VXLanHandler(switchSock, password));
        loop.add(switchSock, EventSet.read(), null, new VProxyHandler(switchSock, vxlanSock, loop, password));
    }

    private static void sendVProxyPacket(VProxySwitchPacket p, ByteBuffer sndBuf, DatagramFD connectedSock) {
        byte[] bytes = p.getRawPacket().toJavaArray();
        sndBuf.limit(sndBuf.capacity()).position(0);
        sndBuf.put(bytes);
        sndBuf.flip();
        try {
            connectedSock.write(sndBuf);
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "sending udp using " + connectedSock + " failed", e);
        }
    }

    public static class VXLanHandler implements Handler<DatagramFD> {
        private final ByteBuffer rcvBuf = ByteBuffer.allocate(2048);
        private final ByteBuffer sndBuf = ByteBuffer.allocate(2048);

        private final DatagramFD switchSock;
        private final Aes256Key passwordKey;

        private ConnectedToVxlanTimer connectedToVxlan = null;

        private static final int toVxlanTimeoutSeconds = 30;

        private class ConnectedToVxlanTimer extends Timer {
            public ConnectedToVxlanTimer(SelectorEventLoop loop) {
                super(loop, toVxlanTimeoutSeconds * 1000);
                Logger.alert("getting packets from vxlan endpoint");
            }

            @Override
            public void cancel() {
                super.cancel();
                connectedToVxlan = null;
                Logger.warn(LogType.ALERT, toVxlanTimeoutSeconds + " seconds no packets from vxlan endpoint");
            }
        }

        public VXLanHandler(DatagramFD switchSock, String password) {
            this.switchSock = switchSock;
            this.passwordKey = new Aes256Key(password);
        }

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            DatagramFD sock = ctx.getChannel();
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                SocketAddress socketAddress;
                try {
                    socketAddress = sock.receive(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                VXLanPacket p = new VXLanPacket();
                ByteArray arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                String err = p.from(arr);
                if (err != null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid packet from " + socketAddress);
                    continue;
                }

                if (connectedToVxlan == null) {
                    connectedToVxlan = new ConnectedToVxlanTimer(ctx.getEventLoop());
                }
                connectedToVxlan.resetTimer();

                assert Logger.lowLevelDebug("received packet " + p);
                sendVXLanPacket(p);
            }
        }

        private void sendVXLanPacket(VXLanPacket vxlan) {
            VProxySwitchPacket p = new VProxySwitchPacket(passwordKey);
            p.magic = Consts.VPROXY_SWITCH_MAGIC;
            p.type = Consts.VPROXY_SWITCH_TYPE_VXLAN;
            p.vxlan = vxlan;
            sendVProxyPacket(p);
        }

        private void sendVProxyPacket(VProxySwitchPacket p) {
            byte[] bytes = p.getRawPacket().toJavaArray();
            sndBuf.limit(sndBuf.capacity()).position(0);
            sndBuf.put(bytes);
            sndBuf.flip();
            try {
                switchSock.write(sndBuf);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "sending udp using " + switchSock + " failed", e);
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // ignore
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            Logger.error(LogType.ALERT, this + " removed from loop");
        }
    }

    public static class VProxyHandler implements Handler<DatagramFD> {
        private final ByteBuffer rcvBuf = ByteBuffer.allocate(2048);
        private final ByteBuffer sndBuf = ByteBuffer.allocate(2048);

        private final DatagramFD switchSock;
        private final DatagramFD vxlanSock;
        private final Aes256Key passwordKey;

        private ConnectedToSwitchTimer connectedToSwitch = null;

        private static final int toSwitchTimeoutSeconds = 60;

        private class ConnectedToSwitchTimer extends Timer {

            public ConnectedToSwitchTimer(SelectorEventLoop loop) {
                super(loop, toSwitchTimeoutSeconds * 1000);
                Logger.alert("connected to switch");
            }

            @Override
            public void cancel() {
                super.cancel();
                connectedToSwitch = null;
                Logger.warn(LogType.ALERT, toSwitchTimeoutSeconds + " seconds no PING response from switch");
            }
        }

        public VProxyHandler(DatagramFD switchSock, DatagramFD vxlanSock, SelectorEventLoop loop, String password) {
            this.switchSock = switchSock;
            this.vxlanSock = vxlanSock;
            passwordKey = new Aes256Key(password);
            this.sendPingPacket();
            loop.period(PING_PERIOD, this::sendPingPacket);
        }

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            DatagramFD sock = ctx.getChannel();
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                SocketAddress socketAddress;
                try {
                    socketAddress = sock.receive(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                VProxySwitchPacket p = new VProxySwitchPacket(passwordKey);
                ByteArray arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                String err = p.from(arr);
                if (err != null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid packet from " + socketAddress);
                    continue;
                }
                if (p.type == Consts.VPROXY_SWITCH_TYPE_PING) {
                    if (connectedToSwitch == null) {
                        connectedToSwitch = new ConnectedToSwitchTimer(ctx.getEventLoop());
                    }
                    connectedToSwitch.resetTimer();
                }
                if (p.vxlan == null) {
                    // not vxlan packet, ignore
                    continue;
                }
                ByteArray vxlan = p.vxlan.getRawPacket();
                sendToVxlan(vxlan);
            }
        }

        private void sendPingPacket() {
            VProxySwitchPacket p = new VProxySwitchPacket(passwordKey);
            p.magic = Consts.VPROXY_SWITCH_MAGIC;
            p.type = Consts.VPROXY_SWITCH_TYPE_PING;
            sendVProxyPacket(p, sndBuf, switchSock);
        }

        private void sendToVxlan(ByteArray vxlan) {
            sndBuf.limit(sndBuf.capacity()).position(0);
            sndBuf.put(vxlan.toJavaArray());
            sndBuf.flip();
            try {
                vxlanSock.write(sndBuf);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "sending udp using " + vxlanSock + " failed", e);
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // ignore
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            Logger.error(LogType.ALERT, this + " removed from loop");
        }
    }
}
