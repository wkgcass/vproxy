package net.cassite.vproxy.app;

import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TcpLBHolder {
    private final Map<String, TcpLB> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias,
                    EventLoopGroup acceptorEventLoopGroup,
                    EventLoopGroup workerEventLoopGroup,
                    InetSocketAddress bindAddress,
                    ServerGroups backends,
                    int inBufferSize,
                    int outBufferSize,
                    SecurityGroup securityGroup) throws AlreadyExistException, IOException, ClosedException {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        TcpLB tcpLB = new TcpLB(alias, acceptorEventLoopGroup, workerEventLoopGroup, bindAddress, backends, inBufferSize, outBufferSize, securityGroup);
        map.put(alias, tcpLB);
        tcpLB.start();
    }

    public TcpLB get(String alias) throws NotFoundException {
        TcpLB tcpLB = map.get(alias);
        if (tcpLB == null)
            throw new NotFoundException();
        return tcpLB;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        TcpLB tl = map.remove(alias);
        if (tl == null)
            throw new NotFoundException();
        tl.destroy();
    }
}
