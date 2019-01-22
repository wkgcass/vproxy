package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.component.proxy.Session;
import net.cassite.vproxy.connection.Connection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ConnectionHandle {
    private ConnectionHandle() {
    }

    public static int count(Resource parent) throws Exception {
        if (parent.type == ResourceType.tl) {

            // get session count and double it
            return SessionHandle.count(parent) * 2;

        } else if (parent.type == ResourceType.el) {

            // try to get connections from event loop
            EventLoopWrapper eventLoop = EventLoopHandle.get(parent);
            return eventLoop.connectionCount();

        } else
            throw new Exception("i don't think that " + parent.type + " contain connections");
    }

    public static List<Connection> list(Resource parent) throws Exception {
        List<Connection> connections;

        if (parent.type == ResourceType.tl) {

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

        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
        return connections;
    }
}
