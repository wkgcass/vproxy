package vproxybase.dns.dnsserverlistgetter;

import vfd.IP;
import vfd.IPPort;
import vproxybase.Config;
import vproxybase.dhcp.DHCPClientHelper;
import vproxybase.dns.AbstractResolver;
import vproxybase.dns.DnsServerListGetter;
import vproxybase.dns.Resolver;
import vproxybase.util.Callback;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

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
