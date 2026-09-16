package io.vproxy.vproxyx.websocks.relay;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vfd.FDs;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.VirtualNetwork;

import java.util.HashSet;

/** Dedicated inbound stack. Outbound connectors continue to use the default OS FDs. */
public class DirectRelayTunSetup {
    private static final int VRF = 1;
    private final Switch sw;
    private final VirtualNetwork network;
    private final IP hostIp;
    private final IP hostIp6;

    private DirectRelayTunSetup(Switch sw, VirtualNetwork network, IP hostIp, IP hostIp6) {
        this.sw = sw;
        this.network = network;
        this.hostIp = hostIp;
        this.hostIp6 = hostIp6;
    }

    public static DirectRelayTunSetup launch(Params p) throws Exception {
        p.validate();
        String dev = p.dev;
        MacAddress mac = p.mac;
        Network v4range = p.v4range;
        Network v6range = p.v6range;
        IP dnsIp = p.dnsIp;
        IP dnsIp6 = p.dnsIp6;
        String postScript = p.postScript;
        // the host side address of the tun device: configured, or the first host address of the range
        IP hostIp = p.hostIp != null ? p.hostIp : hostIP(v4range);
        IP hostIp6 = v6range != null ? (p.hostIp6 != null ? p.hostIp6 : hostIP(v6range)) : null;

        Switch sw = new Switch("vpws-dr", new IPPort("255.255.255.255:0"),
            p.elg, 300_000, 4 * 3600_000, SecurityGroup.denyAll());
        try {
            sw.start();
            VirtualNetwork network = sw.addNetwork(VRF, v4range, v6range, null);
            // TunIface uses its MAC as the source of synthesized Ethernet frames.
            // Using it for the stack too makes Switch drop every input packet.
            byte[] localMacBytes = mac.bytes.toJavaArray().clone();
            localMacBytes[5] ^= 1;
            MacAddress localMac = new MacAddress(localMacBytes);
            network.addIp(dnsIp, localMac, null);
            if (dnsIp6 != null) {
                network.addIp(dnsIp6, localMac, null);
            }
            // the whole direct-relay ranges are owned by the stack
            // (fake ips are NOT registered one by one)
            network.declareLocalIpRange(v4range, localMac);
            if (v6range != null) {
                network.declareLocalIpRange(v6range, localMac);
            }

            // Receiving an externally supplied tun fd needs future infrastructure.
            var tun = sw.addTun(dev, VRF, mac, postScript);
            String realDev = tun.getTun().getTap().dev;
            Logger.alert("direct-relay userspace stack: tun device " + realDev + " created");
            Logger.alert("  ip addr add " + hostIp.formatToIPString() + "/" + v4range.getMask() + " dev " + realDev);
            if (v6range != null) {
                Logger.alert("  ip -6 addr add " + hostIp6.formatToIPString() + "/" + v6range.getMask() + " dev " + realDev);
            }
            Logger.alert("  ip link set dev " + realDev + " up");
            Logger.alert("  point system dns to " + dnsIp.formatToIPString()
                + (dnsIp6 == null ? "" : " / " + dnsIp6.formatToIPString()));
            return new DirectRelayTunSetup(sw, network, hostIp, hostIp6);
        } catch (Exception e) {
            sw.destroy();
            throw e;
        }
    }

    public static class Params {
        private EventLoopGroup elg;
        private Network v4range;
        private Network v6range;
        private String dev;
        private MacAddress mac;
        private String postScript;
        private IP dnsIp;
        private IP dnsIp6;
        private IP hostIp;
        private IP hostIp6;

        public Params setEventLoopGroup(EventLoopGroup elg) {
            this.elg = elg;
            return this;
        }

        public Params setV4Range(Network v4range) {
            this.v4range = v4range;
            return this;
        }

        public Params setV6Range(Network v6range) {
            this.v6range = v6range;
            return this;
        }

        public Params setDev(String dev) {
            this.dev = dev;
            return this;
        }

        public Params setMac(MacAddress mac) {
            this.mac = mac;
            return this;
        }

        public Params setPostScript(String postScript) {
            this.postScript = postScript;
            return this;
        }

        public Params setDnsIp(IP dnsIp) {
            this.dnsIp = dnsIp;
            return this;
        }

        public Params setDnsIp6(IP dnsIp6) {
            this.dnsIp6 = dnsIp6;
            return this;
        }

        public Params setHostIp(IP hostIp) {
            this.hostIp = hostIp;
            return this;
        }

        public Params setHostIp6(IP hostIp6) {
            this.hostIp6 = hostIp6;
            return this;
        }

        public void validate() throws Exception {
            if (elg == null) {
                throw new Exception("event loop group is required for the direct-relay userspace stack");
            }
            if (v4range == null) {
                throw new Exception("v4range is required for the direct-relay userspace stack");
            }
            if (dnsIp == null) {
                throw new Exception("dnsIp is required for the direct-relay userspace stack");
            }
            if ((v6range == null) != (dnsIp6 == null)) {
                throw new Exception("v6range and dnsIp6 must be set together for the direct-relay userspace stack");
            }
            if (hostIp6 != null && v6range == null) {
                throw new Exception("hostIp6 is set but v6range is not set for the direct-relay userspace stack");
            }
            if (dev == null) {
                throw new Exception("dev is required for the direct-relay userspace stack");
            }
            if (mac == null) {
                throw new Exception("mac is required for the direct-relay userspace stack");
            }
        }
    }

    // Derive the default host side address of the TUN: the first host address of the range.
    public static IP hostIP(Network net) {
        byte[] bytes = net.getIp().getAddress().clone();
        bytes[bytes.length - 1] |= 1;
        return IP.from(bytes);
    }

    public VirtualNetwork network() {
        return network;
    }

    public FDs fds() {
        return network.fds();
    }

    public DomainBinder createDomainBinder(SelectorEventLoop loop, Network net) {
        var reserved = new HashSet<>(network.ips.allIps());
        reserved.add(net.getIp() instanceof IPv4 ? hostIp : hostIp6);
        return new DomainBinder(loop, net, reserved);
    }

    public void destroy() {
        sw.destroy();
    }
}
