package vswitch;

import vfd.DatagramFD;
import vfd.EventSet;
import vfd.FDProvider;
import vfd.posix.TunTapDatagramFD;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.ByteArray;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Timer;
import vproxy.util.crypto.Aes256Key;
import vswitch.packet.EthernetPacket;
import vswitch.packet.VProxySwitchPacket;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Base64;

public class VXLanAdaptorHandlers {
    private static final int PING_PERIOD = 20 * 1000;

    private VXLanAdaptorHandlers() {
    }

    public static void launchGeneralAdaptor(SelectorEventLoop loop, InetSocketAddress switchSockAddr, InetSocketAddress vxlanSockAddr, InetSocketAddress listenAddr, String user, String password) throws IOException {
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

            launchGeneralAdaptor(loop, 0, switchSock, vxlanSock, listenSock, user, password);
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

    public static void launchGeneralAdaptor(SelectorEventLoop loop, int flags, DatagramFD switchSock, DatagramFD toDeviceSock, DatagramFD listenSock, String user, String password) throws IOException {
        if (user.length() < 8) {
            user += Consts.USER_PADDING.repeat(8 - user.length());
        }
        if (Base64.getDecoder().decode(user).length != 6) {
            throw new IllegalArgumentException("invalid user: " + user);
        }
        loop.add(listenSock, EventSet.read(), null, new DevicePacketHandler(flags, switchSock, user, password));
        loop.add(switchSock, EventSet.read(), null, new VProxyHandler(flags, switchSock, toDeviceSock, loop, user, password));
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

    public static class DevicePacketHandler implements Handler<DatagramFD> {
        private final ByteBuffer rcvBuf = ByteBuffer.allocate(2048);
        private final ByteBuffer sndBuf = ByteBuffer.allocate(2048);

        private final int flags;
        private final DatagramFD switchSock;
        private final String user;
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

        public DevicePacketHandler(int flags, DatagramFD switchSock, String user, String password) {
            this.flags = flags;
            this.switchSock = switchSock;
            this.user = user;
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

                VXLanPacket p;
                if (flags == 0) {
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
                    p = new VXLanPacket();
                    ByteArray arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                    String err = p.from(arr);
                    if (err != null) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid packet from " + socketAddress + ":  " + err);
                        continue;
                    }
                } else {
                    try {
                        sock.read(rcvBuf);
                    } catch (IOException e) {
                        Logger.error(LogType.CONN_ERROR, "reading from " + ctx.getChannel() + " got error", e);
                        return;
                    }
                    if (rcvBuf.position() == 0) {
                        break; // nothing read, quit loop
                    }
                    ByteArray arr;
                    if ((flags & TunTapDatagramFD.IFF_NO_PI) == TunTapDatagramFD.IFF_NO_PI) {
                        arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                    } else {
                        arr = ByteArray.from(rcvBuf.array()).sub(4, rcvBuf.position() - 4);
                    }
                    EthernetPacket ether = new EthernetPacket();
                    String err = ether.from(arr);
                    if (err != null) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid packet from " + sock + ": " + err);
                        continue;
                    }
                    p = new VXLanPacket();
                    p.setFlags(0b00001000);
                    p.setVni(1); // not important, will be overwritten on the switch side
                    p.setPacket(ether);
                }

                if (connectedToVxlan == null) {
                    connectedToVxlan = new ConnectedToVxlanTimer(ctx.getEventLoop());
                }
                connectedToVxlan.resetTimer();

                assert Logger.lowLevelDebug("the packet to send to switch" + p);
                sendVXLanPacket(p);
            }
        }

        private void sendVXLanPacket(VXLanPacket vxlan) {
            VProxySwitchPacket p = new VProxySwitchPacket(x -> passwordKey);
            p.setUser(user);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_VXLAN);
            p.setVxlan(vxlan);
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

        private final int flags;
        private final DatagramFD switchSock;
        private final DatagramFD vxlanSock;
        private final String user;
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

        public VProxyHandler(int flags, DatagramFD switchSock, DatagramFD vxlanSock, SelectorEventLoop loop, String user, String password) {
            this.flags = flags;
            this.switchSock = switchSock;
            this.vxlanSock = vxlanSock;
            this.user = user;
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
                try {
                    sock.read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                VProxySwitchPacket p = new VProxySwitchPacket(x -> passwordKey);
                ByteArray arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                String err = p.from(arr);
                if (err != null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid packet from " + sock);
                    continue;
                }
                if (!p.getUser().equals(user)) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user in received packet mismatch, got " + p.getUser());
                }
                if (p.getType() == Consts.VPROXY_SWITCH_TYPE_PING) {
                    if (connectedToSwitch == null) {
                        connectedToSwitch = new ConnectedToSwitchTimer(ctx.getEventLoop());
                    }
                    connectedToSwitch.resetTimer();
                }
                if (p.getVxlan() == null) {
                    // not vxlan packet, ignore
                    continue;
                }
                ByteArray packetBytes;
                if (flags == 0) {
                    packetBytes = p.getVxlan().getRawPacket();
                } else {
                    packetBytes = p.getVxlan().getPacket().getRawPacket();
                }
                sendPacketToDevice(packetBytes);
            }
        }

        private void sendPingPacket() {
            VProxySwitchPacket p = new VProxySwitchPacket(x -> passwordKey);
            p.setUser(user);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_PING);
            sendVProxyPacket(p, sndBuf, switchSock);
        }

        private void sendPacketToDevice(ByteArray vxlan) {
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
