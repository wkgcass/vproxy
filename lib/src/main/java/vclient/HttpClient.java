package vclient;

import vclient.impl.Http1ClientImpl;
import vfd.IP;
import vfd.IPPort;
import vlibbase.ConnectionAware;
import vproxybase.dns.Resolver;
import vproxybase.util.ringbuffer.SSLUtils;
import vserver.HttpMethod;

import java.net.UnknownHostException;

public interface HttpClient extends GeneralClient, ConnectionAware<HttpClientConn> {
    static HttpClient to(String host, int port) {
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

    static HttpClient to(IP l3addr, int port) {
        return to(new IPPort(l3addr, port));
    }

    static HttpClient to(IPPort l4addr) {
        return to(l4addr, new Options());
    }

    static HttpClient to(IPPort l4addr, Options opts) {
        return new Http1ClientImpl(l4addr, opts);
    }

    static HttpClient to(String protocolAndHostAndPort) {
        boolean tls = false;
        int port = 80;
        String hostAndPort;
        if (protocolAndHostAndPort.contains("://")) {
            if (!protocolAndHostAndPort.startsWith("http://") && !protocolAndHostAndPort.startsWith("https://")) {
                throw new IllegalArgumentException("unknown protocol in " + protocolAndHostAndPort);
            }
            hostAndPort = protocolAndHostAndPort.substring(protocolAndHostAndPort.indexOf("://") + "://".length());
        } else {
            hostAndPort = protocolAndHostAndPort;
        }
        if (protocolAndHostAndPort.startsWith("https://")) {
            tls = true;
            port = 443;
        }
        IP l3addr;
        String host;
        if (IP.isIpLiteral(hostAndPort)) {
            l3addr = IP.from(hostAndPort);
            host = null;
        } else {
            if (hostAndPort.contains(":")) {
                // host : port
                host = hostAndPort.substring(0, hostAndPort.lastIndexOf(":"));
                String portStr = hostAndPort.substring(hostAndPort.indexOf(":") + 1);
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid port in " + protocolAndHostAndPort);
                }
            } else {
                host = hostAndPort;
            }
            l3addr = IP.blockResolve(host);
        }
        return to(new IPPort(l3addr, port), new Options()
            .setSSLContext(
                tls ? SSLUtils.getDefaultClientSSLContext() : null
            )
            .setHost(host)
        );
    }

    default HttpRequest get(String uri) {
        return request(HttpMethod.GET, uri);
    }

    default HttpRequest pst(String uri) {
        return request(HttpMethod.POST, uri);
    }

    default HttpRequest post(String uri) {
        return pst(uri);
    }

    default HttpRequest put(String uri) {
        return request(HttpMethod.PUT, uri);
    }

    default HttpRequest del(String uri) {
        return request(HttpMethod.DELETE, uri);
    }

    HttpRequest request(HttpMethod method, String uri);

    class Options extends GeneralClientOptions<Options> {
        public int poolSize = 10;
        public boolean autoRelease = true;

        public Options() {
            super();
        }

        public Options(Options that) {
            super(that);
            this.poolSize = that.poolSize;
            this.autoRelease = that.autoRelease;
        }

        public Options setPoolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public Options setAutoRelease(boolean autoRelease) {
            this.autoRelease = autoRelease;
            return this;
        }
    }
}
