package vproxy.app;

import vproxy.component.app.TcpLB;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.ServerGroups;

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
                    int timeout,
                    int inBufferSize,
                    int outBufferSize,
                    String protocol,
                    SecurityGroup securityGroup) throws AlreadyExistException, IOException, ClosedException {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        TcpLB tcpLB = new TcpLB(alias, acceptorEventLoopGroup, workerEventLoopGroup, bindAddress, backends, timeout, inBufferSize, outBufferSize, protocol, securityGroup);
        try {
            tcpLB.start();
        } catch (IOException e) {
            tcpLB.destroy();
            throw e;
        }
        map.put(alias, tcpLB);
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

    void clear() {
        map.clear();
    }

    void put(String alias, TcpLB tcpLB) {
        map.put(alias, tcpLB);
    }
}
