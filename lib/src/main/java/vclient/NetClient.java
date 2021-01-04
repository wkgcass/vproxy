package vclient;

import vclient.impl.NetClientImpl;
import vfd.IP;
import vfd.IPPort;
import vlibbase.Conn;
import vlibbase.ConnectionAware;
import vproxybase.dns.Resolver;

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
