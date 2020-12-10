package vproxybase.dns.dnsserverlistgetter;

import vfd.IP;
import vfd.IPPort;
import vproxybase.dns.DnsServerListGetter;
import vproxybase.util.Callback;
import vproxybase.util.Logger;

import java.util.Arrays;
import java.util.List;

public class GetDnsServerListDefault implements DnsServerListGetter {
    private final List<IPPort> ret = Arrays.asList(
        new IPPort(IP.from(new byte[]{8, 8, 8, 8}), 53),
        new IPPort(IP.from(new byte[]{8, 8, 4, 4}), 53)
    );

    @Override
    public void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb) {
        if (firstRun) {
            Logger.alert("using 8.8.8.8 and 8.8.4.4 as name servers");
        }
        cb.succeeded(ret);
    }
}
