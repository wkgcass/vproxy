package vproxyx.websocks;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ServerList {
    public static class Server {
        public final boolean useSSL;
        public final boolean useKCP;
        public final String host;
        public final int port;

        public Server(boolean useSSL, boolean useKCP, String host, int port) {
            this.useSSL = useSSL;
            this.useKCP = useKCP;
            this.host = host;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Server server = (Server) o;
            return useSSL == server.useSSL &&
                useKCP == server.useKCP &&
                port == server.port &&
                Objects.equals(host, server.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(useSSL, useKCP, host, port);
        }
    }

    private final List<Server> servers = new LinkedList<>();

    public boolean add(boolean useSSL, boolean useKCP, String host, int port) {
        var svr = new Server(useSSL, useKCP, host, port);
        if (servers.contains(svr)) {
            return false;
        }
        servers.add(svr);
        return true;
    }

    public boolean remove(boolean useSSL, boolean useKCP, String host, int port) {
        var foo = new Server(useSSL, useKCP, host, port);
        return servers.remove(foo);
    }

    public List<Server> getServers() {
        return servers;
    }
}
