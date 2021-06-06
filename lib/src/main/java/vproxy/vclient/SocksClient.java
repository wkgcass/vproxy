package vproxy.vclient;

import vproxy.base.dns.Resolver;
import vproxy.vclient.impl.SocksClientImpl;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vlibbase.ConnRef;
import vproxy.vlibbase.Handler;

import java.net.UnknownHostException;

public interface SocksClient extends GeneralClient {
    @SuppressWarnings("DuplicatedCode")
    static SocksClientImpl to(String host, int port) {
        if (IP.isIpLiteral(host)) {
            return to(IP.from(host), port);
        }
        IP ip;
        try {
            ip = Resolver.getDefault().blockResolve(host);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return to(new IPPort(ip, port), new Options());
    }

    static SocksClientImpl to(IP l3addr, int port) {
        return to(new IPPort(l3addr, port));
    }

    static SocksClientImpl to(IPPort l4addr) {
        return to(l4addr, new Options());
    }

    static SocksClientImpl to(IPPort l4addr, Options opts) {
        return new SocksClientImpl(l4addr, opts);
    }

    void proxy(IPPort target, Handler<ConnRef> cb);

    void proxy(String domain, int port, Handler<ConnRef> cb);

    class Options extends GeneralClientOptions<Options> {
        public Options() {
        }

        public Options(Options that) {
            super(that);
        }
    }
}
