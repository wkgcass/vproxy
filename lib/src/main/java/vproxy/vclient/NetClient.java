package vproxy.vclient;

import vproxy.base.dns.Resolver;
import vproxy.vclient.impl.NetClientImpl;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vlibbase.Conn;
import vproxy.vlibbase.ConnectionAware;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.function.BiConsumer;

public interface NetClient extends GeneralClient, ConnectionAware<Conn> {
    @SuppressWarnings("DuplicatedCode")
    static NetClient to(String host, int port) {
        if (IP.isIpLiteral(host)) {
            return to(IP.from(host), port);
        }
        IP ip;
        try {
            ip = Resolver.getDefault().blockResolve(host);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return to(new IPPort(ip, port), new Options().setHost(host));
    }

    static NetClient to(IP l3addr, int port) {
        return to(new IPPort(l3addr, port));
    }

    static NetClient to(IPPort l4addr) {
        return to(l4addr, new Options());
    }

    static NetClient to(IPPort l4addr, Options opts) {
        return new NetClientImpl(l4addr, opts);
    }

    void connect(BiConsumer<IOException, Conn> connectionCallback);

    class Options extends GeneralSSLClientOptions<Options> {
        public Options() {
            super();
        }

        public Options(Options that) {
            super(that);
        }
    }
}
