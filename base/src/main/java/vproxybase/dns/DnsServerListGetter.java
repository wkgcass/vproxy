package vproxybase.dns;

import vfd.IPPort;
import vproxybase.util.Callback;

import java.util.List;

public interface DnsServerListGetter {
    void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb);
}
