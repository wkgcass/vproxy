package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.proxy.Session;

import java.util.LinkedList;
import java.util.List;

public class SessionHandle {
    private SessionHandle() {
    }

    public static void checkSession(Resource session) throws Exception {
        if (session.parentResource == null)
            throw new Exception("cannot find " + session.type.fullname + " on top level");
        if (session.parentResource.type != ResourceType.tl)
            throw new Exception(session.parentResource.type.fullname + " does not contain " + session.type.fullname);
        TcpLBHandle.checkTcpLB(session.parentResource);
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
