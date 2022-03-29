package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ServerSockHandle {
    private ServerSockHandle() {
    }

    public static ServerSock2 get(Resource svr) throws Exception {
        return list(svr.parentResource)
            .stream()
            .filter(bs -> bs.toString().equals(svr.alias))
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
        } else if (parent.type == ResourceType.socks5) {
            return Socks5ServerHandle.get(parent).acceptorGroup.list().size();
        } else {
            assert parent.type == ResourceType.vpc;
            return VpcHandle.get(parent).conntrack.countTcpListenEntry();
        }
    }

    public static List<ServerSock2> list(Resource parent) throws Exception {
        List<ServerSock2> servers;
        if (parent.type == ResourceType.el) {
            List<ServerSock> ls = new LinkedList<>();
            EventLoopHandle.get(parent).copyServers(ls);
            servers = new ArrayList<>(ls.size());
            for (var e : ls) {
                servers.add(new ServerSock2(e));
            }
        } else if (parent.type == ResourceType.socks5) {
            var ls = Socks5ServerHandle.get(parent).servers.keySet();
            servers = new ArrayList<>(ls.size());
            for (var e : ls) {
                servers.add(new ServerSock2(e));
            }
        } else if (parent.type == ResourceType.tl) {
            var ls = TcpLBHandle.get(parent).servers.keySet();
            servers = new ArrayList<>(ls.size());
            for (var e : ls) {
                servers.add(new ServerSock2(e));
            }
        } else {
            assert parent.type == ResourceType.vpc;
            var ls = VpcHandle.get(parent).conntrack.listTcpListenEntries();
            servers = new ArrayList<>(ls.size());
            for (var e : ls) {
                servers.add(new ServerSock2(e));
            }
        }
        return servers;
    }

    public static class ServerSock2 {
        public final IPPort listeningAddress;
        private final ServerSock serverSock;

        public ServerSock2(ServerSock serverSock) {
            this.listeningAddress = serverSock.bind;
            this.serverSock = serverSock;
        }

        public ServerSock2(TcpListenEntry listenEntry) {
            this.listeningAddress = listenEntry.listening;
            this.serverSock = null;
        }

        @Override
        public String toString() {
            return listeningAddress.formatToIPPortString();
        }

        public long getFromRemoteBytes() {
            return serverSock == null ? 0 : serverSock.getFromRemoteBytes();
        }

        public long getToRemoteBytes() {
            return serverSock == null ? 0 : serverSock.getToRemoteBytes();
        }

        public long getHistoryAcceptedConnectionCount() {
            return serverSock == null ? 0 : serverSock.getHistoryAcceptedConnectionCount();
        }
    }
}
