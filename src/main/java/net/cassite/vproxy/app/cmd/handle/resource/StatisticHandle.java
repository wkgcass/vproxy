package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.connection.BindServer;

public class StatisticHandle {
    private StatisticHandle() {
    }

    public static long bytesIn(Resource parent) throws Exception {
        if (parent.type == ResourceType.bs) {
            return BindServerHandle.get(parent).getFromRemoteBytes();
        } else if (parent.type == ResourceType.conn) {
            return ConnectionHandle.get(parent).getFromRemoteBytes();
        } else if (parent.type == ResourceType.svr) {
            return ServerHandle.get(parent).getFromRemoteBytes();
        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
    }

    public static long bytesOut(Resource parent) throws Exception {
        if (parent.type == ResourceType.bs) {
            return BindServerHandle.get(parent).getToRemoteBytes();
        } else if (parent.type == ResourceType.conn) {
            return ConnectionHandle.get(parent).getToRemoteBytes();
        } else if (parent.type == ResourceType.svr) {
            return ServerHandle.get(parent).getToRemoteBytes();
        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
    }

    public static long acceptedConnCount(Resource parent) throws Exception {
        BindServer bs = BindServerHandle.get(parent);
        return bs.getHistoryAcceptedConnectionCount();
    }
}
