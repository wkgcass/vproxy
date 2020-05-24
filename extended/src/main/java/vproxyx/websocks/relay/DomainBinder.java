package vproxyx.websocks.relay;

import vfd.IP;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.TimerEvent;
import vproxybase.util.Lock;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.Utils;

import java.util.HashMap;
import java.util.Map;

public class DomainBinder {
    private final SelectorEventLoop loop;
    private final byte[] network;
    private final int ipLimit;
    private int incr = 0;
    private final Lock lock = Lock.create();
    private final Map<IP, EntryWithTimeout> ipMap = new HashMap<>(1024);
    private final Map<String, EntryWithTimeout> domainMap = new HashMap<>(1024);

    public DomainBinder(SelectorEventLoop loop, String net) {
        this.loop = loop;
        this.network = IP.parseIpString(net.substring(0, net.indexOf("/")));
        int maskInt = Integer.parseInt(net.substring(net.indexOf("/") + 1));
        ipLimit = (int) Math.pow(2, (network.length > 4 ? 128 : 32) - maskInt);
    }

    public IP assignForDomain(String domain, int timeout) {
        //noinspection unused
        try (Lock.Locked l = lock.lock()) {
            var entry = domainMap.get(domain);
            if (entry != null) {
                entry.resetTimer(timeout);
                return entry.l3addr;
            }
            var l3addr = assignNext();
            entry = new EntryWithTimeout(domain, l3addr, timeout);
            domainMap.put(domain, entry);
            ipMap.put(l3addr, entry);
            Logger.trace(LogType.ALERT, "the bond domains now consume " + ipMap.size() + " ips in total " + ipLimit);
            return l3addr;
        }
    }

    private IP assignNext() {
        IP i = assignNext0();
        if (i != null) {
            return i;
        }
        // not found, search again from the beginning
        incr = 0;
        i = assignNext0();
        if (i != null) {
            return i;
        }
        // still not found, reset the cursor and return null
        incr = 0;
        return null;
    }

    private IP assignNext0() {
        while (true) {
            ++incr;
            if (incr >= ipLimit) {
                return null;
            }
            byte[] l3addr = new byte[network.length];
            System.arraycopy(network, 0, l3addr, 0, network.length);
            byte[] sub = Utils.long2bytes(incr);
            for (int i = 0; i < sub.length; ++i) {
                l3addr[l3addr.length - i - 1] = (byte) (l3addr[l3addr.length - i - 1] | sub[sub.length - i - 1]);
            }
            IP inet = IP.from(l3addr);
            if (ipMap.containsKey(inet)) {
                continue;
            }
            return inet;
        }
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
