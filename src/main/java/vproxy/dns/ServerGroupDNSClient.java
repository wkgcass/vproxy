package vproxy.dns;

import vfd.DatagramFD;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerListener;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.Callback;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ServerGroupDNSClient extends DNSClient {
    private final ServerGroup serverGroup;
    private final ServerListener serverListener;

    public ServerGroupDNSClient(SelectorEventLoop loop, DatagramFD sock, ServerGroup serverGroup, int dnsReqTimeout, int maxRetry) throws IOException {
        super(loop, sock, Collections.emptyList(), dnsReqTimeout, maxRetry);
        this.serverGroup = serverGroup;
        setHealthyServeList();
        serverListener = new ServerListener() {
            @Override
            public void up(ServerGroup.ServerHandle server) {
                setHealthyServeList();
            }

            @Override
            public void down(ServerGroup.ServerHandle server) {
                setHealthyServeList();
            }

            @Override
            public void start(ServerGroup.ServerHandle server) {
                setHealthyServeList();
            }

            @Override
            public void stop(ServerGroup.ServerHandle server) {
                setHealthyServeList();
            }
        };
        this.serverGroup.addServerListener(serverListener);
    }

    @Override
    public void setNameServers(List<InetSocketAddress> nameServers) {
        var toAdd = new HashSet<InetSocketAddress>();
        var toDel = new HashSet<ServerGroup.ServerHandle>();
        var servers = serverGroup.getServerHandles();
        out:
        for (InetSocketAddress addr : nameServers) {
            for (ServerGroup.ServerHandle svr : servers) {
                if (svr.server.equals(addr)) {
                    // found
                    continue out;
                }
            }
            // not found
            toAdd.add(addr);
        }
        out:
        for (ServerGroup.ServerHandle svr : servers) {
            for (InetSocketAddress addr : nameServers) {
                if (svr.server.equals(addr)) {
                    // found
                    continue out;
                }
            }
            // not found
            toDel.add(svr);
        }

        // modify
        for (var svr : toDel) {
            try {
                serverGroup.remove(svr.alias);
            } catch (NotFoundException ignore) {
            }
        }
        for (var add : toAdd) {
            try {
                serverGroup.add(Utils.l4addrStr(add), add, 10);
            } catch (AlreadyExistException ignore) {
            }
        }
        if (!toDel.isEmpty() || !toAdd.isEmpty()) {
            setHealthyServeList();
        }
    }

    private void setHealthyServeList() {
        var ls = serverGroup.getServerHandles().stream().filter(s -> s.healthy).map(s -> s.server).collect(Collectors.toList());
        super.setNameServers(ls);
    }

    @Override
    public void resolveIPv4(String domain, Callback<List<InetAddress>, UnknownHostException> cb) {
        super.resolveIPv4(domain, cb);
    }

    @Override
    public void resolveIPv6(String domain, Callback<List<InetAddress>, UnknownHostException> cb) {
        super.resolveIPv6(domain, cb);
    }

    @Override
    public void request(DNSPacket reqPacket, Callback<DNSPacket, IOException> cb) {
        super.request(reqPacket, cb);
    }

    @Override
    public void close() {
        super.close();
        serverGroup.removeServerListener(serverListener);
    }
}
