package net.cassite.vproxy.discovery;

import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;
import net.cassite.vproxy.util.Utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class DiscoveryConfig {
    public final String nic;
    public final String bindAddress;
    final InetAddress bindInetAddress;
    public final int udpSockPort;
    public final int udpPort;
    public final int tcpPort;
    public final String searchNetwork;
    public final int searchMask;
    public final int searchMinUDPPort;
    public final int searchMaxUDPPort;
    final long searchNetworkCursorMaxExclusive;
    final byte[] searchNetworkByte;
    final byte[] searchMaskByte;

    public DiscoveryConfig(String nic,
                           String bindAddress,
                           int udpSockPort,
                           int udpPort,
                           int tcpPort,
                           String searchNetwork, int searchMask,
                           int searchMinUDPPort, int searchMaxUDPPort) throws UnknownHostException {
        this.nic = nic;
        this.udpSockPort = udpSockPort;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        this.searchNetwork = searchNetwork;
        this.searchMask = searchMask;
        this.searchMinUDPPort = searchMinUDPPort;
        this.searchMaxUDPPort = searchMaxUDPPort;

        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolve(bindAddress, cb);
        this.bindInetAddress = cb.block();

        this.searchNetworkByte = Utils.blockParseAddress(bindAddress);
        this.searchMaskByte = Utils.parseMask(searchMask);

        this.searchNetworkCursorMaxExclusive =
            (long) Math.pow(2, ((bindInetAddress instanceof Inet4Address) ? 32 : 128) - searchMask);

        // use the same format for ip
        this.bindAddress = Utils.ipStr(bindInetAddress.getAddress());
    }
}
