package vproxy.app.cmd.handle.resource;

import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.exception.NotFoundException;
import vproxy.connection.ServerSock;

import java.util.LinkedList;
import java.util.List;

public class ServerSockHandle {
    private ServerSockHandle() {
    }

    public static void checkServerSock(Resource server) throws Exception {
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

    public static ServerSock get(Resource svr) throws Exception {
        return list(svr.parentResource)
            .stream()
            .filter(bs -> bs.id().equals(svr.alias))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                "server-sock in " + svr.parentResource.type.fullname + " " + svr.parentResource.alias,
                svr.alias));
    }

    public static int count(Resource parent) throws Exception {
        if (parent.type == ResourceType.el) {
            return EventLoopHandle.get(parent).serverCount();
        } else if (parent.type == ResourceType.tl) {
            return TcpLBHandle.get(parent).acceptorGroup.list().size();
        } else {
            assert parent.type == ResourceType.socks5;
            return Socks5ServerHandle.get(parent).acceptorGroup.list().size();
        }
    }

    public static List<ServerSock> list(Resource parent) throws Exception {
        List<ServerSock> servers = new LinkedList<>();
        if (parent.type == ResourceType.el) {
            EventLoopHandle.get(parent).copyServers(servers);
        } else if (parent.type == ResourceType.socks5) {
            servers.addAll(Socks5ServerHandle.get(parent).servers.keySet());
        } else {
            assert parent.type == ResourceType.tl;
            servers.addAll(TcpLBHandle.get(parent).servers.keySet());
        }
        return servers;
    }
}
