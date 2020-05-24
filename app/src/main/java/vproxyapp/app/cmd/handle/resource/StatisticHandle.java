package vproxyapp.app.cmd.handle.resource;

import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxybase.connection.ServerSock;

public class StatisticHandle {
    private StatisticHandle() {
    }

    public static long bytesIn(Resource parent) throws Exception {
        if (parent.type == ResourceType.ss) {
            return ServerSockHandle.get(parent).getFromRemoteBytes();
        } else if (parent.type == ResourceType.conn) {
            return ConnectionHandle.get(parent).getFromRemoteBytes();
        } else if (parent.type == ResourceType.svr) {
            return ServerHandle.get(parent).getFromRemoteBytes();
        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
    }

    public static long bytesOut(Resource parent) throws Exception {
        if (parent.type == ResourceType.ss) {
            return ServerSockHandle.get(parent).getToRemoteBytes();
        } else if (parent.type == ResourceType.conn) {
            return ConnectionHandle.get(parent).getToRemoteBytes();
        } else if (parent.type == ResourceType.svr) {
            return ServerHandle.get(parent).getToRemoteBytes();
        } else
            throw new Exception("i don't think that " + parent.type + " contains connections");
    }

    public static long acceptedConnCount(Resource parent) throws Exception {
        ServerSock bs = ServerSockHandle.get(parent);
        return bs.getHistoryAcceptedConnectionCount();
    }
}
