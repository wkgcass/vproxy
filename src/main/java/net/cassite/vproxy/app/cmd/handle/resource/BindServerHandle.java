package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.connection.BindServer;

import java.util.LinkedList;
import java.util.List;

public class BindServerHandle {
    private BindServerHandle() {
    }

    public static void checkBindServer(Resource server) throws Exception {
        Resource parent = server.parentResource;
        if (parent == null)
            throw new Exception("cannot find " + server.type.fullname + " on top level");
        if (parent.type == ResourceType.el) {
            EventLoopHandle.checkEventLoop(parent);
        } else if (parent.type == ResourceType.tl) {
            TcpLBHandle.checkTcpLB(parent);
        } else if (parent.type == ResourceType.socks5) {
            Socks5ServerHandle.checkSocks5Server(parent);
        } else {
            throw new Exception(parent.type.fullname + " does not contain " + server.type.fullname);
        }
    }

    public static BindServer get(Resource svr) throws Exception {
        return list(svr.parentResource)
            .stream()
            .filter(bs -> bs.id().equals(svr.alias))
            .findFirst()
            .orElseThrow(NotFoundException::new);
    }

    public static int count(Resource parent) throws Exception {
        if (parent.type == ResourceType.el) {
            return EventLoopHandle.get(parent).serverCount();
        } else {
            assert parent.type == ResourceType.tl || parent.type == ResourceType.socks5;
            return 1;
        }
    }

    public static List<BindServer> list(Resource parent) throws Exception {
        List<BindServer> servers = new LinkedList<>();
        if (parent.type == ResourceType.el) {
            EventLoopHandle.get(parent).copyServers(servers);
        } else if (parent.type == ResourceType.socks5) {
            servers.add(Socks5ServerHandle.get(parent).server);
        } else {
            assert parent.type == ResourceType.tl;
            servers.add(TcpLBHandle.get(parent).server);
        }
        return servers;
    }
}
