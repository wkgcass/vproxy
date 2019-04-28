package net.cassite.vproxy.component.auto;

import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.khala.KhalaNode;
import net.cassite.vproxy.component.khala.KhalaNodeListener;
import net.cassite.vproxy.component.khala.KhalaNodeType;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.discovery.Node;
import net.cassite.vproxy.util.ConcurrentHashSet;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.Set;

public class SmartLBGroup {
    class SmartLBGroupKhalaNodeListener implements KhalaNodeListener {
        @Override
        public void add(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || node.type == KhalaNodeType.nexus || !node.service.equals(service)) {
                return; // ignore the node event if don't care
            }

            String svrName = AutoUtil.utilServerNameFromNode(node);

            @SuppressWarnings("UnnecessaryLocalVariable")
            ServerGroup grp = SmartLBGroup.this.handledGroup;
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

            @SuppressWarnings("UnnecessaryLocalVariable")
            ServerGroup group = SmartLBGroup.this.handledGroup;
            try {
                group.remove(svrName);
            } catch (NotFoundException e) {
                // ignore when not found
            }
        }
    }

    private final SmartLBGroupKhalaNodeListener smartLBGroupKhalaNodeListener = new SmartLBGroupKhalaNodeListener();

    public final String alias;
    public final String service;
    public final String zone;
    public final AutoConfig config;

    public final TcpLB handledLb; // this field is only for recording, and should not be used in any logic
    public final ServerGroup handledGroup;

    private final Set<KhalaNode> khalaNodes = new ConcurrentHashSet<>();

    public SmartLBGroup(String alias, String service, String zone, TcpLB lb, ServerGroup group, AutoConfig config) throws Exception {
        this.alias = alias;
        this.service = service;
        this.zone = zone;
        this.config = config;

        this.handledLb = lb;
        this.handledGroup = group;

        // help to add the group into the lb if not exist yet
        {
            boolean alreadyAdded = false;
            int maxWeight = 0;
            int sumWeight = 0;
            int cnt = 0;
            for (ServerGroups.ServerGroupHandle gh : lb.backends.getServerGroups()) {
                if (gh.group.equals(group)) {
                    alreadyAdded = true;
                    break;
                }
                ++cnt;
                sumWeight += gh.getWeight();
                if (maxWeight < gh.getWeight()) {
                    maxWeight = gh.getWeight();
                }
            }
            if (!alreadyAdded) {
                int avgWeight;
                if (cnt == 0) {
                    avgWeight = 0;
                } else {
                    avgWeight = sumWeight / cnt;
                }
                if (avgWeight == 0) { // in case the weight result is 0, we set it to the max weight
                    avgWeight = maxWeight;
                }
                if (avgWeight == 0) { // in case the max weight is still 0, we set it to 10
                    avgWeight = 10;
                }
                lb.backends.add(group, avgWeight);
            }
        }

        config.khala.addKhalaNodeListener(smartLBGroupKhalaNodeListener);
        KhalaNode kn = new KhalaNode(KhalaNodeType.nexus, service, zone, Utils.ipStr(lb.bindAddress.getAddress().getAddress()), lb.bindAddress.getPort());
        config.khala.addLocal(kn);
        khalaNodes.add(kn);
    }

    public void destroy() {
        config.khala.removeKhalaNodeListener(smartLBGroupKhalaNodeListener);
        for (KhalaNode n : khalaNodes) {
            config.khala.removeLocal(n);
        }
        this.handledGroup.clear(); // remove all servers from the group
        // we ignore those manually added servers because it's not a legal operation
    }
}
