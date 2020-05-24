package vproxyapp.app;

import vfd.IPPort;
import vproxy.component.app.TcpLB;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.Upstream;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.util.Logger;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.ClosedException;
import vproxybase.util.exception.NotFoundException;
import vproxybase.util.ringbuffer.ssl.VSSLContext;

import java.io.IOException;
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
                    IPPort bindAddress,
                    Upstream backend,
                    int timeout,
                    int inBufferSize,
                    int outBufferSize,
                    String protocol,
                    CertKey[] sslCertKeys,
                    SecurityGroup securityGroup) throws AlreadyExistException, IOException, ClosedException, Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException("tcp-lb", alias);

        VSSLContext sslContext = buildVSSLContext(sslCertKeys);

        TcpLB tcpLB = new TcpLB(alias, acceptorEventLoopGroup, workerEventLoopGroup, bindAddress, backend, timeout, inBufferSize, outBufferSize, protocol, sslContext, sslCertKeys, securityGroup);
        try {
            tcpLB.start();
        } catch (IOException e) {
            tcpLB.destroy();
            throw e;
        }
        map.put(alias, tcpLB);
    }

    public VSSLContext buildVSSLContext(CertKey[] sslCertKeys) throws Exception {
        VSSLContext sslContext;
        if (sslCertKeys != null) {
            try {
                // build ssl context if needed
                // create ctx
                VSSLContext ctx = new VSSLContext();
                for (CertKey ck : sslCertKeys) {
                    ck.setInto(ctx);
                }
                // assign
                sslContext = ctx;
            } catch (Exception e) {
                Logger.shouldNotHappen("initiate ssl context for lb failed", e);
                throw e;
            }
        } else {
            sslContext = null;
        }
        return sslContext;
    }

    public TcpLB get(String alias) throws NotFoundException {
        TcpLB tcpLB = map.get(alias);
        if (tcpLB == null)
            throw new NotFoundException("tcp-lb", alias);
        return tcpLB;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        TcpLB tl = map.remove(alias);
        if (tl == null)
            throw new NotFoundException("tcp-lb", alias);
        tl.destroy();
    }
}
