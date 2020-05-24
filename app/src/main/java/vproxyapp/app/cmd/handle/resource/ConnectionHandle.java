package vproxyapp.app.cmd.handle.resource;

import vproxy.component.proxy.Session;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxybase.component.elgroup.EventLoopWrapper;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.connection.Connection;
import vproxybase.util.exception.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class ConnectionHandle {
    private ConnectionHandle() {
    }

    public static void checkConnection(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.conn.fullname + " on top level");
        if (parent.type == ResourceType.el) {
            EventLoopHandle.checkEventLoop(parent);
        } else if (parent.type == ResourceType.tl) {
            TcpLBHandle.checkTcpLB(parent);
        } else if (parent.type == ResourceType.socks5) {
            Socks5ServerHandle.checkSocks5Server(parent);
        } else if (parent.type == ResourceType.svr) {
            ServerHandle.checkServer(parent);
        } else {
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.conn.fullname);
        }
    }

    public static Connection get(Resource resource) throws Exception {
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

        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
    }

    public static List<Connection> list(Resource parent) throws Exception {
        List<Connection> connections;

        if (parent.type == ResourceType.tl || parent.type == ResourceType.socks5) {

            // get sessions
            List<Session> sessions = SessionHandle.list(parent);

            // create a list of session size * 2 (for active and passive connections)
            connections = new ArrayList<>(sessions.size() * 2);
            for (Session s : sessions) {
                connections.add(s.active);
                connections.add(s.passive);
            }

        } else if (parent.type == ResourceType.el) {

            // try to get connections from event loop
            EventLoopWrapper eventLoop = EventLoopHandle.get(parent);
            connections = new LinkedList<>();
            eventLoop.copyConnections(connections);

        } else if (parent.type == ResourceType.svr) {

            // try to get connections from server
            ServerGroup.ServerHandle h = ServerHandle.get(parent);
            connections = new LinkedList<>();
            h.copyConnections(connections);

        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
        return connections;
    }

    public static void close(Command cmd) throws Exception {
        List<Connection> connections = list(cmd.prepositionResource);
        String pattern = cmd.resource.alias;
        Pattern p = null;
        if (pattern.startsWith("/") && pattern.endsWith("/")) {
            p = Pattern.compile(pattern.substring(1, pattern.length() - 1));
        }
        for (Connection c : connections) {
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
}
