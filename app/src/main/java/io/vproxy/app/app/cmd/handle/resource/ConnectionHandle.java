package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.component.proxy.Session;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vswitch.VirtualNetwork;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class ConnectionHandle {
    private ConnectionHandle() {
    }

    public static Conn get(Resource resource) throws Exception {
        return list(resource.parentResource)
            .stream()
            .filter(c -> c.id().equals(resource.alias))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                "connection in " + resource.parentResource.type.fullname + " " + resource.parentResource.alias,
                resource.alias
            ));
    }

    public static int count(Resource parent) throws Exception {
        if (parent.type == ResourceType.tl || parent.type == ResourceType.socks5) {

            // get session count and double it
            return SessionHandle.count(parent) * 2;

        } else if (parent.type == ResourceType.el) {

            // try to get connections from event loop
            EventLoopWrapper eventLoop = EventLoopHandle.get(parent);
            return eventLoop.connectionCount();

        } else if (parent.type == ResourceType.svr) {

            ServerGroup.ServerHandle h = ServerHandle.get(parent);
            return h.connectionCount();

        } else if (parent.type == ResourceType.vpc) {

            VirtualNetwork t = VpcHandle.get(parent);
            return t.conntrack.countTcpEntries();

        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
    }

    public static List<Conn> list(Resource parent) throws Exception {
        List<Conn> connections;

        if (parent.type == ResourceType.tl || parent.type == ResourceType.socks5) {

            // get sessions
            List<Session> sessions = SessionHandle.list(parent);

            // create a list of session size * 2 (for active and passive connections)
            connections = new ArrayList<>(sessions.size() * 2);
            for (Session s : sessions) {
                connections.add(new Conn(s.active, true));
                connections.add(new Conn(s.passive, false));
            }

        } else if (parent.type == ResourceType.el) {

            // try to get connections from event loop
            EventLoopWrapper eventLoop = EventLoopHandle.get(parent);
            List<Connection> conns = new LinkedList<>();
            eventLoop.copyConnections(conns);
            connections = new ArrayList<>(conns.size());
            for (var c : conns) {
                connections.add(new Conn(c));
            }

        } else if (parent.type == ResourceType.svr) {

            // try to get connections from server
            ServerGroup.ServerHandle h = ServerHandle.get(parent);
            List<Connection> conns = new LinkedList<>();
            h.copyConnections(conns);
            connections = new ArrayList<>(conns.size());
            for (var c : conns) {
                connections.add(new Conn(c));
            }

        } else if (parent.type == ResourceType.vpc) {

            // try to get connections from switch-network
            VirtualNetwork network = VpcHandle.get(parent);
            Collection<TcpEntry> entries = network.conntrack.listTcpEntries();
            connections = new ArrayList<>(entries.size());
            for (var t : entries) {
                connections.add(new Conn(t));
            }

        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
        return connections;
    }

    public static void close(Command cmd) throws Exception {
        List<Conn> connections = list(cmd.prepositionResource);
        String pattern = cmd.resource.alias;
        Pattern p = null;
        if (pattern.startsWith("/") && pattern.endsWith("/")) {
            p = Pattern.compile(pattern.substring(1, pattern.length() - 1));
        }
        for (Conn c : connections) {
            //noinspection Duplicates
            if (p == null) {
                // directly compare
                if (c.id().equals(pattern)) {
                    c.close();
                    // there can be no other connection with the same id
                    break;
                }
            } else {
                // regex test
                if (p.matcher(c.id()).find()) {
                    c.close();
                    // then continue
                }
            }
        }
    }

    public static class Conn {
        public final IPPort remote;
        public final IPPort local;
        public final String state;
        private final Connection conn;

        public Conn(TcpEntry tcp) {
            this.remote = tcp.remote;
            this.local = tcp.local;
            this.state = tcp.getState().name();
            this.conn = null;
        }

        public Conn(Connection conn) {
            this(conn, false);
        }

        public Conn(Connection conn, boolean active) {
            if (active) {
                remote = conn.getLocal();
                local = conn.remote;
            } else {
                remote = conn.remote;
                local = conn.getLocal();
            }
            if (conn.isClosed()) {
                state = "CLOSED";
            } else if (conn.isRemoteClosed() && conn.isWriteClosed()) {
                state = "CLOSING";
            } else if (conn.isRemoteClosed()) {
                state = "CLOSE_WAIT";
            } else if (conn.isWriteClosed()) {
                state = "FIN_WAIT_1";
            } else {
                state = "ESTABLISHED";
            }
            this.conn = conn;
        }

        @Override
        public String toString() {
            return remote.formatToIPPortString() + "/" + local.formatToIPPortString() + "[" + state + "]";
        }

        public String id() {
            return remote.formatToIPPortString() + "/" + local.formatToIPPortString();
        }

        public void close() {
            if (conn == null) {
                throw new UnsupportedOperationException("");
            }
            conn.close();
        }

        public long getFromRemoteBytes() {
            return conn == null ? 0 : conn.getFromRemoteBytes();
        }

        public long getToRemoteBytes() {
            return conn == null ? 0 : conn.getToRemoteBytes();
        }
    }
}
