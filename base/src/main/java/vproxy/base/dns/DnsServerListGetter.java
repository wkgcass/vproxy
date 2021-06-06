package vproxy.base.dns;

import vproxy.base.util.Callback;
import vproxy.vfd.IPPort;

import java.util.List;

public interface DnsServerListGetter {
    void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb);
}
