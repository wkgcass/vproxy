package io.vproxy.app.app;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.dns.DNSServer;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DNSServerHolder {
    private final Map<String, DNSServer> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias,
                    IPPort bindAddress,
                    EventLoopGroup eventLoopGroup,
                    Upstream backend,
                    int ttl,
                    SecurityGroup securityGroup) throws AlreadyExistException, ClosedException, IOException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("dns-server", alias);

        DNSServer dnsServer = new DNSServer(alias, bindAddress, eventLoopGroup, backend, ttl, securityGroup);
        try {
            dnsServer.start();
        } catch (IOException e) {
            dnsServer.stop();
            throw e;
        }
        map.put(alias, dnsServer);
    }

    public DNSServer get(String alias) throws NotFoundException {
        DNSServer dnsServer = map.get(alias);
        if (dnsServer == null)
            throw new NotFoundException("dns-server", alias);
        return dnsServer;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        DNSServer dnsServer = map.remove(alias);
        if (dnsServer == null)
            throw new NotFoundException("dns-server", alias);
        dnsServer.stop();
    }
}
