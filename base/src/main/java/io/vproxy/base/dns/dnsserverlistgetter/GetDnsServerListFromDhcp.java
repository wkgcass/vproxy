package io.vproxy.base.dns.dnsserverlistgetter;

import io.vproxy.base.Config;
import io.vproxy.base.dhcp.DHCPClientHelper;
import io.vproxy.base.dns.AbstractResolver;
import io.vproxy.base.dns.DnsServerListGetter;
import io.vproxy.base.dns.Resolver;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

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
