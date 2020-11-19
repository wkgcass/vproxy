package vproxybase.dhcp;

import vfd.*;
import vproxybase.dhcp.options.DNSOption;
import vproxybase.dhcp.options.MessageTypeOption;
import vproxybase.dhcp.options.ParameterRequestListOption;
import vproxybase.selector.Handler;
import vproxybase.selector.HandlerContext;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.TimerEvent;
import vproxybase.util.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DHCPClientHelper {
    public static final int DHCP_CLIENT_PORT = 68;
    public static final int DHCP_SERVER_PORT = 67;

    private DHCPClientHelper() {
    }

    public static void getDomainNameServers(SelectorEventLoop loop, int maxFailedRetry, Callback<Set<IP>, IOException> cb) {
        getDomainNameServers(loop, new Callback<>() {
            @Override
            protected void onSucceeded(Set<IP> value) {
                cb.succeeded(value);
            }

            @Override
            protected void onFailed(IOException err) {
                if (maxFailedRetry == 0) {
                    cb.failed(err);
                } else {
                    Logger.warn(LogType.ALERT, "getDomainNameServers failed, retries left " + maxFailedRetry, err);
                    getDomainNameServers(loop, maxFailedRetry - 1, cb);
                }
            }
        });
    }

    public static void getDomainNameServers(SelectorEventLoop loop, Callback<Set<IP>, IOException> cb) {
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            cb.failed(e);
            return;
        }

        // local-mac, remote-ip, local-ip
        List<Tuple3<MacAddress, IP, IP>> todo = new LinkedList<>();

        while (interfaces.hasMoreElements()) {
            var iface = interfaces.nextElement();
            byte[] hardwareAddress;
            try {
                hardwareAddress = iface.getHardwareAddress();
            } catch (SocketException e) {
                Logger.shouldNotHappen("get hardware address of nic " + iface.getName() + " failed", e);
                continue;
            }
            if (hardwareAddress == null) {
                assert Logger.lowLevelDebug("hardware address of nic " + iface.getName() + " is null");
                continue;
            }
            if (hardwareAddress.length != 6) {
                Logger.shouldNotHappen("hardware address length is not 6 of nic " + iface.getName());
                continue;
            }
            MacAddress mac = new MacAddress(hardwareAddress);

            List<InterfaceAddress> interfaceAddressList = iface.getInterfaceAddresses();
            for (var addr : interfaceAddressList) {
                var broadcast = addr.getBroadcast();
                if (!(broadcast instanceof Inet4Address)) {
                    continue;
                }
                var broadcastIp = IP.from(broadcast);
                var localIP = IP.from(addr.getAddress());
                //                local-mac, remote-ip, local-ip
                todo.add(new Tuple3<>(mac, broadcastIp, localIP));

                break;
            }
        }

        if (todo.isEmpty()) {
            cb.failed(new IOException("no nic or address found for broadcasting"));
            return;
        }

        AtomicInteger count = new AtomicInteger();
        //noinspection unchecked
        Set<IP>[] ipSetResults = new Set[todo.size()];
        IOException[] exceptions = new IOException[todo.size()];
        int idx = 0;
        for (Tuple3<MacAddress, IP, IP> tup : todo) {
            final int ii = idx;
            idx += 1;
            //                    remote-ip, local-ip, local-mac
            getDomainNameServers(loop, tup._2, tup._3, tup._1, new Callback<>() {
                @Override
                protected void onSucceeded(Set<IP> value) {
                    ipSetResults[ii] = value;
                }

                @Override
                protected void onFailed(IOException err) {
                    exceptions[ii] = err;
                }

                @Override
                protected void doFinally() {
                    int cnt = count.incrementAndGet();
                    if (cnt != todo.size()) {
                        return;
                    }

                    // finished
                    Set<IP> result = new HashSet<>();
                    for (var ips : ipSetResults) {
                        if (ips == null) {
                            continue;
                        }
                        result.addAll(ips);
                    }
                    if (result.isEmpty()) {
                        StringBuilder errors = new StringBuilder();
                        boolean isFirst = true;
                        for (var err : exceptions) {
                            assert err != null;
                            if (isFirst) {
                                isFirst = false;
                            } else {
                                errors.append("\n");
                            }
                            errors.append(tup).append(err.getMessage());
                        }
                        cb.failed(new IOException(errors.toString()));
                    } else {
                        cb.succeeded(result);
                    }
                }
            });
        }
    }

    private static void getDomainNameServers(SelectorEventLoop loop, IP remoteBroadcast, IP localIP, MacAddress localMac, Callback<Set<IP>, IOException> cb) {
        DHCPPacket pkt = new DHCPPacket();
        pkt.op = Consts.DHCP_OP_BOOTREQUEST;
        pkt.chaddr = localMac;
        pkt.options.add(new MessageTypeOption(Consts.DHCP_MSG_TYPE_DHCPDISCOVER));
        pkt.options.add(new ParameterRequestListOption().add(Consts.DHCP_OPT_TYPE_DNS));

        new DHCPClientHelper.Request(loop, remoteBroadcast, localIP, pkt, 1_000, new Callback<>() {
            @Override
            protected void onSucceeded(Map<IPPort, List<DHCPPacket>> value) {
                Set<IP> result = new HashSet<>();
                for (var packets : value.values()) {
                    for (var pkt : packets) {
                        if (pkt.op != Consts.DHCP_OP_BOOTREPLY) {
                            assert Logger.lowLevelDebug("received non-replay dhcp packet: " + pkt);
                            continue;
                        }
                        var optMsgType = pkt.options.stream().filter(o -> o instanceof MessageTypeOption).findAny();
                        if (optMsgType.isEmpty()) {
                            assert Logger.lowLevelDebug("received pkt without msg type option: " + pkt);
                            continue;
                        }
                        if (((MessageTypeOption) optMsgType.get()).msgType != Consts.DHCP_MSG_TYPE_DHCPOFFER) {
                            assert Logger.lowLevelDebug("received pkt msg type not DHCPOFFER: " + pkt);
                            continue;
                        }
                        for (var opt : pkt.options) {
                            if (opt instanceof DNSOption) {
                                result.addAll(((DNSOption) opt).dnsList);
                            }
                        }
                    }
                }
                if (result.isEmpty()) {
                    cb.failed(new IOException("no dns server received"));
                } else {
                    cb.succeeded(result);
                }
            }

            @Override
            protected void onFailed(IOException err) {
                cb.failed(err);
            }
        }).run();
    }

    public static final class Request {
        private final SelectorEventLoop loop;
        private final IP remoteBroadcast;
        private final IP localIP;
        private final DHCPPacket reqPacket;
        private final int waitTime;
        private final Callback<Map<IPPort, List<DHCPPacket>>, IOException> callback;

        private TimerEvent timer;
        private DatagramFD sock;

        private boolean launched = false;
        private boolean finished = false;

        private final Map<IPPort, List<DHCPPacket>> result = new HashMap<>();

        public Request(SelectorEventLoop loop, IP remoteBroadcast, IP localIP, DHCPPacket reqPacket, int waitTime, Callback<Map<IPPort, List<DHCPPacket>>, IOException> callback) {
            this.loop = loop;
            this.remoteBroadcast = remoteBroadcast;
            this.localIP = localIP;
            this.reqPacket = reqPacket;
            this.waitTime = waitTime;
            this.callback = callback;
        }

        public void run() {
            if (launched) {
                throw new IllegalArgumentException("this Request object cannot be reused");
            }
            launched = true;
            try {
                sock = FDProvider.get().openDatagramFD();
            } catch (IOException e) {
                cbFail(e);
                return;
            }
            timer = loop.delay(waitTime, this::timerEvent);

            try {
                sock.configureBlocking(false);
                sock.setOption(StandardSocketOptions.SO_BROADCAST, true);
                sock.bind(new IPPort(localIP, DHCP_CLIENT_PORT));
                sock.send(ByteBuffer.wrap(reqPacket.serialize().toJavaArray()), new IPPort(remoteBroadcast, DHCP_SERVER_PORT));
                loop.add(sock, EventSet.read(), null, new Handler<>() {
                    private final ByteBuffer buf = ByteBuffer.allocate(1500); // usually mtu <= 1500, and is definitely enough for dhcp packets

                    @Override
                    public void accept(HandlerContext<DatagramFD> ctx) {
                        // ignore
                    }

                    @Override
                    public void connected(HandlerContext<DatagramFD> ctx) {
                        // ignore
                    }

                    @Override
                    public void readable(HandlerContext<DatagramFD> ctx) {
                        while (true) {
                            // reset buf positions
                            buf.position(0).limit(buf.capacity());

                            IPPort serverIPPort;
                            try {
                                serverIPPort = sock.receive(buf);
                            } catch (IOException e) {
                                cbFail(e);
                                return;
                            }
                            assert serverIPPort == null || Logger.lowLevelDebug("received dhcp message from " + serverIPPort);
                            buf.flip();
                            if (buf.limit() == 0) {
                                buf.limit(buf.capacity());
                                return; // still waiting for data
                            }
                            DHCPPacket result = new DHCPPacket();
                            try {
                                result.deserialize(ByteArray.from(buf).copy());
                            } catch (Exception e) {
                                cbFail(new IOException("failed parsing external packet", e));
                                return;
                            }
                            oneSuccess(serverIPPort, result);
                        }
                    }

                    @Override
                    public void writable(HandlerContext<DatagramFD> ctx) {
                        // ignore
                    }

                    @Override
                    public void removed(HandlerContext<DatagramFD> ctx) {
                        if (!finished) {
                            Logger.error(LogType.IMPROPER_USE, "the dhcp is running but fd removed from loop " + sock);
                            cbFail(new IOException("removed from loop"));
                        }
                    }
                });
            } catch (IOException e) {
                cbFail(e);
                //noinspection UnnecessaryReturnStatement
                return;
            }
        }

        private void closeSock() {
            loop.remove(sock);
            try {
                sock.close();
            } catch (IOException ignore) {
            }
        }

        private void timerEvent() {
            finished = true;
            closeSock();
            if (result.isEmpty()) {
                callback.failed(new IOException("timeout"));
            } else {
                callback.succeeded(result);
            }
        }

        private void oneSuccess(IPPort ipport, DHCPPacket p) {
            result.computeIfAbsent(ipport, x -> new LinkedList<>()).add(p);
        }

        private void cbFail(IOException ex) {
            finished = true;
            closeSock();
            timer.cancel();
            callback.failed(ex);
        }
    }
}
