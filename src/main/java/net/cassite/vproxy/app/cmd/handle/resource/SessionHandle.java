package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.proxy.Session;

import java.util.LinkedList;
import java.util.List;

public class SessionHandle {
    private SessionHandle() {
    }

    public static int count(Resource parent) throws Exception {
        // get session count from tcp loadbalancer
        TcpLB lb = Application.get().tcpLBHolder.get(parent.alias);
        return lb.sessionCount();
    }

    public static List<Session> list(Resource parent) throws Exception {
        TcpLB lb = Application.get().tcpLBHolder.get(parent.alias);

        // retrieve sessions
        List<Session> sessions = new LinkedList<>();
        lb.copySessions(sessions);

        return sessions;
    }
}
