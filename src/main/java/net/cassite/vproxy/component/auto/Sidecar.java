package net.cassite.vproxy.component.auto;

import net.cassite.vproxy.component.app.Socks5Server;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.exception.*;
import net.cassite.vproxy.component.khala.KhalaNode;
import net.cassite.vproxy.component.khala.KhalaNodeListener;
import net.cassite.vproxy.component.khala.KhalaNodeType;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.discovery.Node;
import net.cassite.vproxy.util.ConcurrentHashSet;
import net.cassite.vproxy.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Sidecar {
    class SidecarKhalaNodeListener implements KhalaNodeListener {
        @Override
        public void add(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || node.type == KhalaNodeType.pylon) {
                return; // ignore the node event if don't care
            }

            String grpName = AutoUtil.utilServerGroupNameFromServiceName(alias, "nexus", node.service);
            String svrName = AutoUtil.utilServerNameFromNode(node);

            List<ServerGroups.ServerGroupHandle> groups = socks5Server.backends.getServerGroups();
            ServerGroup group = null;
            for (ServerGroups.ServerGroupHandle h : groups) {
                if (h.group.alias.equals(grpName)) {
                    group = h.group;
                    break;
                }
            }
            if (group == null) {
                // should create a new group
                ServerGroup grp;
                try {
                    grp = new ServerGroup(grpName, config.workerGroup, config.hcConfig, config.selectMethod);
                } catch (AlreadyExistException | ClosedException e) {
                    Logger.shouldNotHappen("create server group failed", e);
                    return;
                }
                try {
                    socks5Server.backends.add(grp, 10);
                } catch (AlreadyExistException e) {
                    Logger.shouldNotHappen("add group into serverGroups failed", e);
                    return;
                }
                group = grp;
            }

            try {
                group.add(svrName, new InetSocketAddress(node.address, node.port), config.bindInetAddress, 10);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("add server into group failed", e);
            }
        }

        @Override
        public void remove(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || node.type == KhalaNodeType.pylon) {
                return; // ignore the node event if don't care
            }

            String grpName = AutoUtil.utilServerGroupNameFromServiceName(alias, "nexus", node.service);
            String svrName = AutoUtil.utilServerNameFromNode(node);

            List<ServerGroups.ServerGroupHandle> groups = socks5Server.backends.getServerGroups();
            ServerGroup group = null;
            for (ServerGroups.ServerGroupHandle h : groups) {
                if (h.group.alias.equals(grpName)) {
                    group = h.group;
                    break;
                }
            }
            if (group == null) {
                return; // ignore if group not found
            }
            // should remove from group
            try {
                group.remove(svrName);
            } catch (NotFoundException e) {
                // ignore err if not found
            }
            if (group.getServerHandles().isEmpty()) {
                // remove the group if it's empty now
                try {
                    socks5Server.backends.remove(group);
                } catch (NotFoundException e) {
                    // ignore if not found
                }
            }
        }
    }

    private static final Object _MAINTAIN_FLAG_ = new Object();

    private final SidecarKhalaNodeListener sidecarKhalaNodeListener = new SidecarKhalaNodeListener();

    public final String alias;
    public final String zone;
    public final int localPort;

    public final AutoConfig config;
    public final int minPort;
    public final int maxPort;

    public final Socks5Server socks5Server;
    private final TcpLB[] lbs;

    private final Set<KhalaNode> khalaNodes = new ConcurrentHashSet<>();

    public Sidecar(String alias, String zone, int localPort,
                   AutoConfig config, int minPort, int maxPort) throws Exception {
        this.alias = alias;
        this.zone = zone;
        this.localPort = localPort;
        this.config = config;

        this.minPort = minPort;
        this.maxPort = maxPort;

        this.socks5Server = createSocks5Server();
        this.lbs = new TcpLB[maxPort - minPort + 1];

        config.khala.addKhalaNodeListener(sidecarKhalaNodeListener);
    }

    public synchronized void addService(String service, int localServicePort) throws Exception {
        String lbName = AutoUtil.utilLBNameFromServiceName(alias, service);
        String serverName = AutoUtil.utilServerNameFromNode(
            new KhalaNode(KhalaNodeType.pylon, service, zone, "127.0.0.1", localServicePort));

        TcpLB tcpLB = null;
        for (TcpLB lb : lbs) {
            if (lb == null)
                continue;
            if (lb.alias.equals(lbName)) {
                tcpLB = lb;
                break;
            }
        }
        if (tcpLB == null) {
            // we should create an lb
            int portIdx = -1;
            for (int i = 0; i < lbs.length; ++i) {
                if (lbs[i] == null) {
                    portIdx = i;
                    break;
                }
            }
            if (portIdx == -1) {
                throw new XException("no available port to create lb");
            }
            int port = minPort + portIdx;
            lbs[portIdx] = createTcpLBServer(port, service);
            tcpLB = lbs[portIdx];
        }

        ServerGroup sg = tcpLB.backends.getServerGroups().get(0).group;
        try {
            sg.add(serverName,
                new InetSocketAddress("127.0.0.1", localServicePort),
                InetAddress.getByName("127.0.0.1"),
                10);
        } catch (AlreadyExistException e) {
            // remove maintain flag
            for (ServerGroup.ServerHandle h : sg.getServerHandles()) {
                if (h.alias.equals(serverName)) {
                    assert h.data == null || h.data == _MAINTAIN_FLAG_;
                    h.data = null; // remove _MAINTAIN_FLAG_
                }
            }
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("got exception when adding the svr", e);
            throw e;
        }
        // remove those with _MAINTAIN_FLAG_ because there is at least one alive process
        {
            Set<String> foo = new HashSet<>();
            for (ServerGroup.ServerHandle h : sg.getServerHandles()) {
                if (h.data == _MAINTAIN_FLAG_) {
                    foo.add(h.alias);
                }
            }
            for (String bar : foo) {
                try {
                    sg.remove(bar);
                } catch (NotFoundException ignore) {
                }
            }
        }

        // add into khala
        KhalaNode kn = new KhalaNode(KhalaNodeType.pylon, service, zone,
            config.bindAddress, tcpLB.bindAddress.getPort());
        config.khala.addLocal(kn);
        khalaNodes.add(kn);
    }

    public synchronized void removeService(String service) throws StillRunningException {
        String lbName = AutoUtil.utilLBNameFromServiceName(alias, service);

        TcpLB tcpLB = null;
        int idx = -1;
        for (int i = 0; i < lbs.length; i++) {
            TcpLB lb = lbs[i];
            if (lb == null)
                continue;
            if (lb.alias.equals(lbName)) {
                tcpLB = lb;
                idx = i;
                break;
            }
        }
        if (tcpLB == null)
            return; // ignore if tcp lb not found

        ServerGroup grp = tcpLB.backends.getServerGroups().get(0).group;
        if (grp.getServerHandles().size() > 1 /* > 1 definitely means still running */) {
            throw new StillRunningException();
        }
        if (grp.getServerHandles().size() == 1 && grp.getServerHandles().get(0).data != _MAINTAIN_FLAG_) {
            throw new StillRunningException();
        }

        // remove all nodes in the group in case got some left
        grp.clear();

        // should remove the tcp lb if it not exists
        tcpLB.destroy();
        lbs[idx] = null;

        // remove from khala
        KhalaNode kn = new KhalaNode(KhalaNodeType.pylon, service, zone,
            config.bindAddress, tcpLB.bindAddress.getPort());
        config.khala.removeLocal(kn);
        khalaNodes.remove(kn);
    }

    public synchronized void maintain(String service, int localServicePort) {
        String lbName = AutoUtil.utilLBNameFromServiceName(alias, service);
        String serverName = AutoUtil.utilServerNameFromNode(
            new KhalaNode(KhalaNodeType.pylon, service, zone, "127.0.0.1", localServicePort));

        TcpLB tcpLB = null;
        for (TcpLB lb : lbs) {
            if (lb.alias.equals(lbName)) {
                tcpLB = lb;
                break;
            }
        }
        if (tcpLB == null)
            return; // ignore if tcp lb not found

        ServerGroup grp = tcpLB.backends.getServerGroups().get(0).group;
        // check whether the server exists in the group
        if (grp.getServerHandles().stream().noneMatch(h -> h.alias.equals(serverName))) {
            return; // ignore if not exists
        }
        if (grp.getServerHandles().size() == 1) {
            // this is the only server in the group

            // so should remove from khala
            KhalaNode kn = new KhalaNode(KhalaNodeType.pylon, service, zone,
                config.bindAddress, tcpLB.bindAddress.getPort());
            config.khala.removeLocal(kn);
            khalaNodes.remove(kn);

            // set _MAINTAIN_FLAG_
            grp.getServerHandles().get(0).data = _MAINTAIN_FLAG_;
        } else {
            // no need to remove from khala
            // just remove the server
            try {
                grp.remove(serverName);
            } catch (NotFoundException e) {
                // ignore if not found
                // however it should not happen
                Logger.shouldNotHappen("got exception when removing server from group", e);
            }
        }
    }

    public void destory() {
        config.khala.removeKhalaNodeListener(sidecarKhalaNodeListener);
        for (KhalaNode n : khalaNodes) {
            config.khala.removeLocal(n);
        }
        socks5Server.destroy();
        for (TcpLB lb : lbs) {
            if (lb == null)
                continue;
            lb.destroy();
        }
    }

    public List<TcpLB> getTcpLBs() {
        List<TcpLB> list = new LinkedList<>();
        for (TcpLB lb : lbs) {
            if (lb == null)
                continue;
            list.add(lb);
        }
        return list;
    }

    private Socks5Server createSocks5Server() throws Exception {
        String lbName = AutoUtil.utilLBNameFromServiceName(alias, "sidecar:" + localPort);
        String sgsName = AutoUtil.utilSgsName(alias, "(sidecar)");

        ServerGroups sgs = new ServerGroups(sgsName);
        Socks5Server socks5Server;
        try {
            socks5Server = new Socks5Server(lbName,
                config.acceptorGroup, config.workerGroup,
                new InetSocketAddress("127.0.0.1" /*listen on local address*/, localPort),
                sgs, 16384, 16384, SecurityGroup.allowAll());
        } catch (IOException | ClosedException | AlreadyExistException e) {
            Logger.shouldNotHappen("got exception when creating socks5 server", e);
            throw e;
        }
        try {
            socks5Server.start();
        } catch (IOException e) {
            Logger.shouldNotHappen("got exception when starting the socks5 server", e);
            throw e;
        }
        return socks5Server;
    }

    private TcpLB createTcpLBServer(int port, String service) throws Exception {
        String lbName = AutoUtil.utilLBNameFromServiceName(alias, service);
        String sgsName = AutoUtil.utilSgsName(alias, service);
        String groupName = AutoUtil.utilServerGroupNameFromServiceName(alias, "local", service);

        ServerGroups sgs = new ServerGroups(sgsName);
        ServerGroup sg;
        try {
            sg = new ServerGroup(groupName, config.workerGroup, config.hcConfig, config.selectMethod);
        } catch (AlreadyExistException | ClosedException e) {
            Logger.shouldNotHappen("got exception when creating the server group", e);
            throw e;
        }
        try {
            sgs.add(sg, 10);
        } catch (AlreadyExistException e) {
            Logger.shouldNotHappen("got exception when adding group into groups", e);
            throw e;
        }
        TcpLB tl;
        try {
            tl = new TcpLB(lbName,
                config.acceptorGroup, config.workerGroup,
                new InetSocketAddress(config.bindInetAddress, port),
                sgs, 16384, 16384, SecurityGroup.allowAll(), 0);
        } catch (IOException | ClosedException | AlreadyExistException e) {
            Logger.shouldNotHappen("got exception when creating tcp lb", e);
            throw e;
        }
        try {
            tl.start();
        } catch (IOException e) {
            Logger.shouldNotHappen("got exception when starting the tcp lb", e);
            throw e;
        }

        return tl;
    }
}
