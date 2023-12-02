package io.vproxy.base.dns;

import io.vproxy.base.Config;
import io.vproxy.base.dns.dnsserverlistgetter.GetDnsServerListDefault;
import io.vproxy.base.dns.dnsserverlistgetter.GetDnsServerListFromConfigFile;
import io.vproxy.base.dns.dnsserverlistgetter.GetDnsServerListFromDhcp;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.vfd.IPPort;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface DnsServerListGetter {
    default void get(Callback<List<IPPort>, Throwable> cb) {
        get(false, cb);
    }

    void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb);

    static List<DnsServerListGetter> allGetters() {
        if (Config.dhcpGetDnsListEnabled) {
            return Arrays.asList(
                new GetDnsServerListFromDhcp(), new GetDnsServerListFromConfigFile(), new GetDnsServerListDefault());
        } else {
            return Arrays.asList(
                new GetDnsServerListFromConfigFile(), new GetDnsServerListDefault());
        }
    }

    static List<DnsServerListGetter> allGettersNoDefault() {
        if (Config.dhcpGetDnsListEnabled) {
            return Arrays.asList(
                new GetDnsServerListFromDhcp(), new GetDnsServerListFromConfigFile());
        } else {
            return Collections.singletonList(
                new GetDnsServerListFromConfigFile());
        }
    }
}
