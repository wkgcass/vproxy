package io.vproxy.base.dns;

import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.component.svrgroup.ServerListener;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.component.svrgroup.ServerListener;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ServerGroupDNSClient extends DNSClient {
    private final ServerGroup serverGroup;
    private final ServerListener serverListener;

    public ServerGroupDNSClient(SelectorEventLoop loop, DatagramFD sock, ServerGroup serverGroup, int dnsReqTimeout, int maxRetry) throws IOException {
        super(loop, sock, dnsReqTimeout, maxRetry);
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
    public void setNameServers(List<IPPort> nameServers) {
        var toAdd = new HashSet<IPPort>();
        var toDel = new HashSet<ServerGroup.ServerHandle>();
        var servers = serverGroup.getServerHandles();
        out:
        for (IPPort addr : nameServers) {
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
            for (IPPort addr : nameServers) {
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
                serverGroup.add(add.formatToIPPortString(), add, 10);
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
    public void resolveIPv4(String domain, Callback<List<IP>, UnknownHostException> cb) {
        super.resolveIPv4(domain, cb);
    }

    @Override
    public void resolveIPv6(String domain, Callback<List<IP>, UnknownHostException> cb) {
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
