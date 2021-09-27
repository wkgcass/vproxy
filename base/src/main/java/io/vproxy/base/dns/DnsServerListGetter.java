package io.vproxy.base.dns;

import io.vproxy.base.util.callback.Callback;
import io.vproxy.vfd.IPPort;

import java.util.List;

public interface DnsServerListGetter {
    void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb);
}
