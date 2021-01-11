package vproxyx.websocks.relay;

import vfd.IP;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.TimerEvent;
import vproxybase.util.*;
import vproxybase.util.crypto.CryptoUtils;

import java.util.HashMap;
import java.util.Map;

public class DomainBinder {
    private final SelectorEventLoop loop;
    private final byte[] network;
    private final int ipLimit;
    private int incr = 1; // begin at 1 to skip the network address
    private final Lock lock = Lock.create();
    private final Map<IP, EntryWithTimeout> ipMap = new HashMap<>(1024);
    private final Map<String, EntryWithTimeout> domainMap = new HashMap<>(1024);

    public DomainBinder(SelectorEventLoop loop, Network net) {
        this.loop = loop;
        this.network = net.getIp().getAddress();
        int maskInt = net.getMask();
        double ipLimitDouble = Math.pow(2, (network.length > 4 ? 128 : 32) - maskInt) - 2; // -2 to remove network and broadcast address
        ipLimit = (ipLimitDouble > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) ipLimitDouble;
    }

    public IP assignForDomain(String domain, int timeout) {
        //noinspection unused
        try (Lock.Locked l = lock.lock()) {
            var entry = domainMap.get(domain);
            if (entry != null) {
                entry.resetTimer(timeout);
                return entry.l3addr;
            }
            var l3addr = assignNext(domain);
            entry = new EntryWithTimeout(domain, l3addr, timeout);
            domainMap.put(domain, entry);
            ipMap.put(l3addr, entry);
            Logger.trace(LogType.ALERT, "the bond domains now consume " + ipMap.size() + " ips in total " + ipLimit);
            return l3addr;
        }
    }

    private IP assignNext(String domain) {
        // first use hash to try to keep the old ip

        long hash = CryptoUtils.md5ToPositiveLong(domain.getBytes());
        Logger.alert("hash of domain " + domain + " is " + hash);
        if (hash < 0) {
            hash = -hash;
        }
        int off = (int) (hash % ipLimit) + 1; // +1 to skip the network address
        IP i = buildIPFromIncr(off);
        if (!ipMap.containsKey(i)) {
            return i;
        }
        Logger.warn(LogType.ALERT, "cannot use hash-generated ip for " + domain + ", choose one instead");
        // it's already allocated, so try to choose a free ip
        i = assignNext0();
        if (i != null) {
            return i;
        }
        // not found, search again from the beginning
        incr = 1;
        i = assignNext0();
        if (i != null) {
            return i;
        }
        // still not found, reset the cursor and return null
        incr = 1;
        return null;
    }

    private IP assignNext0() {
        while (true) {
            ++incr;
            if (incr > ipLimit) {
                return null;
            }
            IP inet = buildIPFromIncr(incr);
            if (ipMap.containsKey(inet)) {
                continue;
            }
            return inet;
        }
    }

    private IP buildIPFromIncr(int incr) {
        byte[] l3addr = new byte[network.length];
        System.arraycopy(network, 0, l3addr, 0, network.length);
        byte[] sub = Utils.long2bytes(incr);
        for (int i = 0; i < sub.length; ++i) {
            l3addr[l3addr.length - i - 1] = (byte) (l3addr[l3addr.length - i - 1] | sub[sub.length - i - 1]);
        }
        return IP.from(l3addr);
    }

    public String getDomain(IP l3addr) {
        //noinspection unused
        try (Lock.Locked l = lock.lock()) {
            var entry = ipMap.get(l3addr);
            if (entry == null) {
                return null;
            }
            entry.resetTimer();
            return entry.domain;
        }
    }

    private class EntryWithTimeout {
        final String domain;
        final IP l3addr;
        TimerEvent e;
        int lastTimeout;

        private EntryWithTimeout(String domain, IP l3addr, int timeout) {
            this.domain = domain;
            this.l3addr = l3addr;
            lastTimeout = timeout;
            resetTimer(timeout);
        }

        private void resetTimer() {
            resetTimer(-1);
        }

        private void resetTimer(int timeout) {
            if (timeout <= 0) {
                timeout = lastTimeout;
            }
            if (e != null) {
                e.cancel();
            }
            if (timeout <= 0) {
                return; // ignore timeout if set to <= 0
            }
            e = loop.delay(lastTimeout, () -> {
                //noinspection unused
                try (Lock.Locked l = lock.lock()) {
                    ipMap.remove(l3addr);
                    domainMap.remove(domain);
                }
            });
        }
    }
}
