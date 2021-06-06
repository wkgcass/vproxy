package vproxy.base.dns.dnsserverlistgetter;

import vproxy.base.Config;
import vproxy.base.dhcp.DHCPClientHelper;
import vproxy.base.dns.AbstractResolver;
import vproxy.base.dns.DnsServerListGetter;
import vproxy.base.dns.Resolver;
import vproxy.base.util.Callback;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GetDnsServerListFromDhcp implements DnsServerListGetter {
    @Override
    public void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb) {
        DHCPClientHelper.getDomainNameServers(((AbstractResolver) Resolver.getDefault()).getLoop().getSelectorEventLoop(),
            Config.dhcpGetDnsListNics, 1, new Callback<>() {
                @Override
                protected void onSucceeded(Set<IP> nameServerIPs) {
                    cb.succeeded(nameServerIPs.stream().map(ip -> new IPPort(ip, 53)).collect(Collectors.toList()));
                }

                @Override
                protected void onFailed(IOException err) {
                    if (firstRun) {
                        Logger.error(LogType.ALERT, "failed retrieving dns servers from dhcp", err);
                    }
                    cb.failed(err);
                }
            });
    }
}
