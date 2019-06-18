package vproxy.app;

import vproxy.app.mesh.SidecarHolder;
import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerGroups;

public class ServiceMeshResourceSynchronizer {
    private ServiceMeshResourceSynchronizer() {
    }

    public static void sync() {
        Application.get().clear();

        syncSidecar(Application.get().sidecarHolder);
    }

    private static void syncSidecar(SidecarHolder sidecarHolder) {
        if (sidecarHolder.getSidecar() == null)
            return;
        syncSocks5(sidecarHolder.getSidecar().socks5Server);
        for (TcpLB lb : sidecarHolder.getSidecar().getTcpLBs()) {
            syncTcpLB(lb);
        }
    }

    private static void syncTcpLB(TcpLB tcpLB) {
        Application.get().tcpLBHolder.put(tcpLB.alias, tcpLB);

        ServerGroups backends = tcpLB.backends;
        syncServerGroups(tcpLB.alias, backends);

        syncEventLoopGroup(tcpLB.acceptorGroup);
        syncEventLoopGroup(tcpLB.workerGroup);
    }

    private static void syncSocks5(Socks5Server socks5) {
        Application.get().socks5ServerHolder.put(socks5.alias, socks5);

        ServerGroups backends = socks5.backends;
        syncServerGroups(socks5.alias, backends);

        syncEventLoopGroup(socks5.acceptorGroup);
        syncEventLoopGroup(socks5.workerGroup);
    }

    private static void syncServerGroups(String topAlias, ServerGroups sgs) {
        Application.get().serverGroupsHolder.put(sgs.alias, sgs);
        for (ServerGroups.ServerGroupHandle h : sgs.getServerGroups()) {
            syncServerGroup(topAlias, h.group);
        }
    }

    private static void syncServerGroup(String topAlias, ServerGroup sg) {
        Application.get().serverGroupHolder.put(topAlias + ":" + sg.alias, sg);
    }

    private static void syncEventLoopGroup(EventLoopGroup elg) {
        Application.get().eventLoopGroupHolder.put(elg.alias, elg);
    }
}
