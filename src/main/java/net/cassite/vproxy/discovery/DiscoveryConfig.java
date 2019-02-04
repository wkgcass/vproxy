package net.cassite.vproxy.discovery;

import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.util.IPType;
import net.cassite.vproxy.util.Utils;

import java.net.*;
import java.util.Enumeration;

public class DiscoveryConfig {
    public final String nic;
    public final String bindAddress;
    public final int udpSockPort;
    public final int udpPort;
    public final int tcpPort;
    public final String searchNetwork;
    public final int searchMask;
    public final int searchMinUDPPort;
    public final int searchMaxUDPPort;
    public final TimeoutConfig timeoutConfig;
    public final HealthCheckConfig healthCheckConfig;
    final long searchNetworkCursorMaxExclusive;
    final byte[] searchNetworkByte;
    final byte[] searchMaskByte;
    long searchMaxCount;

    public final InetAddress bindInetAddress;

    public DiscoveryConfig(String nicName,
                           IPType ipType,
                           int udpSockPort,
                           int udpPort,
                           int tcpPort,
                           int searchMask,
                           int searchMinUDPPort, int searchMaxUDPPort,
                           TimeoutConfig timeoutConfig,
                           HealthCheckConfig healthCheckConfig) throws SocketException {
        this.nic = nicName;
        this.udpSockPort = udpSockPort;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        this.searchMask = searchMask;
        this.searchMinUDPPort = searchMinUDPPort;
        this.searchMaxUDPPort = searchMaxUDPPort;
        this.timeoutConfig = timeoutConfig;
        this.healthCheckConfig = healthCheckConfig;

        Inet4Address v4Addr = null;
        Inet6Address v6Addr = null;
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nicName.equals(nic.getDisplayName())) {
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress a = addresses.nextElement();
                    if (a instanceof Inet4Address) {
                        v4Addr = (Inet4Address) a;
                    } else if (a instanceof Inet6Address) {
                        v6Addr = (Inet6Address) a;
                    }
                    if (v4Addr != null && v6Addr != null)
                        break; // both v4 and v6 found
                }
                break; // nic found, so break
            }
        }
        if (v4Addr == null && v6Addr == null)
            throw new SocketException("nic " + nicName + " not found or no ip address");
        if (ipType == IPType.v4 && v4Addr == null)
            throw new SocketException("nic " + nicName + " do not have a v4 ip");
        if (ipType == IPType.v6 && v6Addr == null)
            throw new SocketException("nic " + nicName + " do not have a v6 ip");
        if (ipType == IPType.v4) {
            //noinspection ConstantConditions
            assert v4Addr != null;
            bindInetAddress = v4Addr;
        } else {
            assert v6Addr != null;
            bindInetAddress = v6Addr;
        }
        bindAddress = Utils.ipStr(bindInetAddress.getAddress());

        byte[] searchMaskByte = Utils.parseMask(searchMask);
        if (ipType == IPType.v6 && searchMaskByte.length <= 4) {
            byte[] foo = new byte[16];
            System.arraycopy(searchMaskByte, 0, foo, 0, 4);
            searchMaskByte = foo;
        }
        this.searchMaskByte = searchMaskByte;
        byte[] searchNetworkByte = new byte[searchMaskByte.length];
        byte[] ipByte = bindInetAddress.getAddress();
        assert ipByte.length == searchMaskByte.length;
        // calculate the network address
        for (int i = 0; i < ipByte.length; ++i) {
            searchNetworkByte[i] = (byte) (ipByte[i] & searchMaskByte[i]);
        }

        this.searchNetwork = Utils.ipStr(searchNetworkByte);
        this.searchNetworkByte = searchNetworkByte;

        this.searchNetworkCursorMaxExclusive =
            (long) Math.pow(2, ((bindInetAddress instanceof Inet4Address) ? 32 : 128) - searchMask);

        this.searchMaxCount = this.searchNetworkCursorMaxExclusive * (searchMaxUDPPort - searchMinUDPPort + 1/*inclusive*/);
    }
}
