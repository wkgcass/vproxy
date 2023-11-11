package io.vproxy.vproxyx.websocks;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ServerList {
    public static class Server {
        public static final int USE_SSL = 0x1;
        public static final int USE_KCP = 0x2;
        public static final int USE_UOT = 0x4;
        public static final int USE_QUIC = 0x8;

        public final int flags;
        public final String host;
        public final int port;

        public Server(boolean useSSL, boolean useKCP, boolean useUOT, boolean useQuic, String host, int port) {
            int flags = 0;
            if (useSSL) {
                flags |= Server.USE_SSL;
            }
            if (useKCP) {
                flags |= Server.USE_KCP;
            }
            if (useUOT) {
                flags |= Server.USE_UOT;
            }
            if (useQuic) {
                flags |= Server.USE_QUIC;
            }
            this.flags = flags;
            this.host = host;
            this.port = port;
        }

        public boolean useSSL() {
            return (flags & USE_SSL) != 0;
        }

        public boolean useKCP() {
            return (flags & USE_KCP) != 0;
        }

        public boolean useUOT() {
            return (flags & USE_UOT) != 0;
        }

        public boolean useQuic() {
            return (flags & USE_QUIC) != 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Server server = (Server) o;
            return flags == server.flags &&
                port == server.port &&
                Objects.equals(host, server.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flags, host, port);
        }
    }

    private final List<Server> servers = new LinkedList<>();

    public boolean add(boolean useSSL, boolean useKCP, boolean useUOT, boolean useQuic, String host, int port) {
        var svr = new Server(useSSL, useKCP, useUOT, useQuic, host, port);
        if (servers.contains(svr)) {
            return false;
        }
        servers.add(svr);
        return true;
    }

    public boolean remove(boolean useSSL, boolean useKCP, boolean useUOT, boolean useQuic, String host, int port) {
        var foo = new Server(useSSL, useKCP, useUOT, useQuic, host, port);
        return servers.remove(foo);
    }

    public List<Server> getServers() {
        return servers;
    }
}
