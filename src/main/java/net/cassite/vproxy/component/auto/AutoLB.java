package net.cassite.vproxy.component.auto;

import net.cassite.vproxy.app.Config;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
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
import java.net.InetSocketAddress;
import java.util.Set;

public class AutoLB {
    class AutoLBKhalaNodeListener implements KhalaNodeListener {
        @Override
        public void add(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || node.type == KhalaNodeType.nexus || !node.service.equals(service)) {
                return; // ignore the node event if don't care
            }

            String svrName = AutoUtil.utilServerNameFromNode(node);

            ServerGroup grp = lb.backends.getServerGroups().get(0).group;
            try {
                grp.add(svrName, new InetSocketAddress(node.address, node.port), 10);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("add server into group failed", e);
            }
        }

        @Override
        public void remove(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || node.type == KhalaNodeType.nexus || !node.service.equals(service)) {
                return; // ignore the node event if don't care
            }

            String svrName = AutoUtil.utilServerNameFromNode(node);

            ServerGroup group = lb.backends.getServerGroups().get(0).group;
            try {
                group.remove(svrName);
            } catch (NotFoundException e) {
                // ignore when not found
            }
        }
    }

    private final AutoLBKhalaNodeListener autoLBKhalaNodeListener = new AutoLBKhalaNodeListener();

    public final String alias;
    public final String service;
    public final String zone;
    public final AutoConfig config;

    public final TcpLB lb;

    private final Set<KhalaNode> khalaNodes = new ConcurrentHashSet<>();

    public AutoLB(String alias, String service, String zone, int port, AutoConfig config) throws Exception {
        this.alias = alias;
        this.service = service;
        this.zone = zone;
        this.config = config;

        String lbName = AutoUtil.utilLBNameFromServiceName(alias, service);
        String sgsName = AutoUtil.utilSgsName(alias, service);
        String groupName = AutoUtil.utilServerGroupNameFromServiceName(service);

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
                sgs,
                Config.tcpTimeout,
                16384, 16384, SecurityGroup.allowAll(), 0);
        } catch (ClosedException | AlreadyExistException e) {
            Logger.shouldNotHappen("got exception when creating tcp lb", e);
            throw e;
        }
        try {
            tl.start();
        } catch (IOException e) {
            Logger.shouldNotHappen("got exception when starting the tcp lb", e);
            throw e;
        }
        this.lb = tl;

        config.khala.addKhalaNodeListener(autoLBKhalaNodeListener);
        KhalaNode kn = new KhalaNode(KhalaNodeType.nexus, service, zone, config.bindAddress, port);
        config.khala.addLocal(kn);
        khalaNodes.add(kn);
    }

    public void destroy() {
        config.khala.removeKhalaNodeListener(autoLBKhalaNodeListener);
        for (KhalaNode n : khalaNodes) {
            config.khala.removeLocal(n);
        }
        lb.backends.getServerGroups().get(0).group.clear();
        lb.destroy();
    }
}
