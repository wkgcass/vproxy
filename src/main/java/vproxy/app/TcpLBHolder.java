package vproxy.app;

import vproxy.component.app.TcpLB;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.ServerGroups;
import vproxy.util.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
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
                    CertKey[] sslCertKeys,
                    SecurityGroup securityGroup) throws AlreadyExistException, IOException, ClosedException, Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException();

        SSLContext sslContext;
        if (sslCertKeys != null) {
            try {
                // build ssl context if needed
                // create ctx
                SSLContext ctx = SSLContext.getInstance("TLS");
                // create empty key store
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null);
                // init keystore
                for (CertKey ck : sslCertKeys) {
                    ck.setInto(keyStore);
                }
                // retrieve key manager array
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(keyStore, "changeit".toCharArray());
                KeyManager[] km = kmf.getKeyManagers();
                // init ctx
                ctx.init(km, null, null);
                // assign
                sslContext = ctx;
            } catch (Exception e) {
                Logger.shouldNotHappen("initiate ssl context for lb failed", e);
                throw e;
            }
        } else {
            sslContext = null;
        }

        TcpLB tcpLB = new TcpLB(alias, acceptorEventLoopGroup, workerEventLoopGroup, bindAddress, backends, timeout, inBufferSize, outBufferSize, protocol, sslContext, sslCertKeys, securityGroup);
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
