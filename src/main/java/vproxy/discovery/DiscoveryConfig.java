package vproxy.discovery;

import vproxy.component.check.HealthCheckConfig;
import vproxy.util.IPType;
import vproxy.util.Utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;

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
    public final TimeConfig timeConfig;
    public final HealthCheckConfig healthCheckConfig;
    final long searchNetworkCursorMaxExclusive;
    final byte[] searchNetworkByte;
    final byte[] searchMaskByte;
    long searchMaxCount;

    public final InetAddress bindInetAddress;
    public final int initialMinSearch;

    public DiscoveryConfig(String nicName,
                           IPType ipType,
                           int udpSockPort,
                           int udpPort,
                           int tcpPort,
                           int searchMask,
                           int searchMinUDPPort, int searchMaxUDPPort,
                           TimeConfig timeConfig,
                           HealthCheckConfig healthCheckConfig) throws SocketException {
        this.nic = nicName;
        this.udpSockPort = udpSockPort;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        this.searchMask = searchMask;
        this.searchMinUDPPort = searchMinUDPPort;
        this.searchMaxUDPPort = searchMaxUDPPort;
        this.timeConfig = timeConfig;
        this.healthCheckConfig = healthCheckConfig;

        bindInetAddress = Utils.getInetAddressFromNic(nicName, ipType);
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
        this.initialMinSearch = (int) Math.min(256, this.searchMaxCount);
    }
}
