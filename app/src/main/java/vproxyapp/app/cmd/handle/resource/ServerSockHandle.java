package vproxyapp.app.cmd.handle.resource;

import vfd.IPPort;
import vpacket.conntrack.tcp.ListenEntry;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxybase.connection.ServerSock;
import vproxybase.util.exception.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ServerSockHandle {
    private ServerSockHandle() {
    }

    public static void checkServerSockParent(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.ss.fullname + " on top level");
        if (parent.type == ResourceType.el) {
            EventLoopHandle.checkEventLoop(parent);
        } else if (parent.type == ResourceType.tl) {
            TcpLBHandle.checkTcpLB(parent);
        } else if (parent.type == ResourceType.socks5) {
            Socks5ServerHandle.checkSocks5Server(parent);
        } else if (parent.type == ResourceType.vpc) {
            VpcHandle.checkVpc(parent);
        } else {
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.ss.fullname);
        }
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
            return VpcHandle.get(parent).conntrack.countListenEntry();
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
            var ls = VpcHandle.get(parent).conntrack.listListenEntries();
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

        public ServerSock2(ListenEntry listenEntry) {
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
