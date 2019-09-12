package vproxy.component.auto;

import vjson.JSON;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.khala.KhalaNode;
import vproxy.component.khala.KhalaNodeListener;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.discovery.Node;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.net.InetSocketAddress;

public class SmartGroupDelegate {
    class SmartGroupDelegateKhalaNodeListener implements KhalaNodeListener {
        @Override
        public void add(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || !node.service.equals(service)) {
                return; // ignore the node event if don't care
            }
            int weight;
            {
                if (node.meta.containsKey("weight") && node.meta.get("weight") instanceof JSON.Integer) {
                    weight = node.meta.getInt("weight");
                } else {
                    weight = 10;
                }
                if (weight < 0) { // handle some exception state
                    Logger.warn(LogType.ALERT, "the external node info have weight < 0, consider it to be 0: " + node);
                    weight = 0;
                }
            }

            String svrName = AutoUtil.utilServerNameFromNode(node);

            @SuppressWarnings("UnnecessaryLocalVariable")
            ServerGroup grp = SmartGroupDelegate.this.handledGroup;
            try {
                grp.add(svrName, new InetSocketAddress(node.address, node.port), weight);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("add server into group failed", e);
            }
        }

        @Override
        public void remove(Node n, KhalaNode node) {
            if (!node.zone.equals(zone) || !node.service.equals(service)) {
                return; // ignore the node event if don't care
            }

            String svrName = AutoUtil.utilServerNameFromNode(node);

            @SuppressWarnings("UnnecessaryLocalVariable")
            ServerGroup group = SmartGroupDelegate.this.handledGroup;
            try {
                group.remove(svrName);
            } catch (NotFoundException e) {
                // ignore when not found
            }
        }
    }

    private final SmartGroupDelegateKhalaNodeListener smartGroupDelegateKhalaNodeListener = new SmartGroupDelegateKhalaNodeListener();

    public final String alias;
    public final String service;
    public final String zone;
    public final AutoConfig config;

    public final ServerGroup handledGroup;

    public SmartGroupDelegate(String alias,
                              String service,
                              String zone,
                              ServerGroup group,
                              AutoConfig config) {
        this.alias = alias;
        this.service = service;
        this.zone = zone;
        this.config = config;
        this.handledGroup = group;

        config.khala.addKhalaNodeListener(smartGroupDelegateKhalaNodeListener);
    }

    public void destroy() {
        config.khala.removeKhalaNodeListener(smartGroupDelegateKhalaNodeListener);
        this.handledGroup.clear(); // remove all servers from the group
        // we ignore those manually added servers because it's not a legal operation
    }
}
