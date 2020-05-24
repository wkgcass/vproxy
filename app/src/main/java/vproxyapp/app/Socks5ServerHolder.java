package vproxyapp.app;

import vfd.IPPort;
import vproxy.component.app.Socks5Server;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.ClosedException;
import vproxybase.util.exception.NotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Socks5ServerHolder {
    private final Map<String, Socks5Server> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public Socks5Server add(String alias,
                            EventLoopGroup acceptorEventLoopGroup,
                            EventLoopGroup workerEventLoopGroup,
                            IPPort bindAddress,
                            Upstream backend,
                            int timeout,
                            int inBufferSize,
                            int outBufferSize,
                            SecurityGroup securityGroup) throws AlreadyExistException, IOException, ClosedException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("socks5-server", alias);
        Socks5Server socks5Server = new Socks5Server(alias, acceptorEventLoopGroup, workerEventLoopGroup, bindAddress, backend, timeout, inBufferSize, outBufferSize, securityGroup);
        try {
            socks5Server.start();
        } catch (IOException e) {
            socks5Server.destroy();
            throw e;
        }
        map.put(alias, socks5Server);
        return socks5Server;
    }

    public Socks5Server get(String alias) throws NotFoundException {
        Socks5Server socks5Server = map.get(alias);
        if (socks5Server == null)
            throw new NotFoundException("socks5-server", alias);
        return socks5Server;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        Socks5Server socks5Server = map.remove(alias);
        if (socks5Server == null)
            throw new NotFoundException("socks5-server", alias);
        socks5Server.destroy();
    }
}
