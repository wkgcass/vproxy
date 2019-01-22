package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.connection.BindServer;

import java.util.LinkedList;
import java.util.List;

public class BindServerHandle {
    private BindServerHandle() {
    }

    public static int count(Resource parent) throws Exception {
        return EventLoopHandle.get(parent).serverCount();
    }

    public static List<BindServer> list(Resource parent) throws Exception {
        List<BindServer> servers = new LinkedList<>();
        EventLoopHandle.get(parent).copyServers(servers);
        return servers;
    }
}
