package vproxy.vswitch;

import vproxy.base.connection.Connector;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.connection.ServerSock;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyEventHandler;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.vfd.IPPort;
import vproxy.vswitch.stack.fd.VSwitchFDContext;
import vproxy.vswitch.stack.fd.VSwitchFDs;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyHolder {
    private final ConcurrentHashMap<IPPort, ProxyRecord> proxies = new ConcurrentHashMap<>();
    private final NetEventLoop loop;
    private final SwitchContext swCtx;
    private final Table table;

    public ProxyHolder(NetEventLoop loop, SwitchContext swCtx, Table table) {
        this.loop = loop;
        this.swCtx = swCtx;
        this.table = table;
    }

    public Collection<ProxyRecord> listRecords() {
        return proxies.values();
    }

    public ProxyRecord lookup(IPPort listen) throws NotFoundException {
        var ret = proxies.get(listen);
        if (ret == null) {
            throw new NotFoundException("proxy", listen.formatToIPPortString());
        }
        return ret;
    }

    public void add(IPPort listen, IPPort target) throws AlreadyExistException, IOException {
        var record = new ProxyRecord(listen, target);
        var old = proxies.putIfAbsent(listen, record);
        if (old != null) {
            throw new AlreadyExistException("proxy", listen.formatToIPPortString());
        }
        try {
            record.start();
        } catch (IOException e) {
            record.stop();
            proxies.remove(listen);
        }
    }

    public void remove(IPPort listen) throws NotFoundException {
        var record = proxies.remove(listen);
        if (record == null) {
            throw new NotFoundException("proxy", listen.formatToIPPortString());
        }
        record.stop();
    }

    public class ProxyRecord {
        public final IPPort listen;
        public final IPPort target;

        private ServerSock sock;
        public Proxy proxy;

        private ProxyRecord(IPPort listen, IPPort target) {
            this.listen = listen;
            this.target = target;
        }

        public void start() throws IOException {
            sock = ServerSock.create(listen, new VSwitchFDs(new VSwitchFDContext(
                swCtx, table, loop.getSelectorEventLoop().selector
            )));

            var eventHandler = new ProxyEventHandler() {
                @Override
                public void serverRemoved(ServerSock server) {

                }
            };
            proxy = new Proxy(new ProxyNetConfig()
                .setAcceptLoop(loop)
                .setConnGen((accepted, hint) -> new Connector(target))
                .setHandleLoopProvider(acceptedLoop -> loop)
                .setInBufferSize(24576)
                .setOutBufferSize(24576)
                .setServer(sock),
                eventHandler);

            proxy.handle();
        }

        public void stop() {
            if (proxy != null) {
                proxy.stop();
                proxy = null;
            }
            if (sock != null) {
                sock.close();
                sock = null;
            }
        }

        public String toString() {
            return listen.formatToIPPortString() + " -> " + target.formatToIPPortString();
        }
    }
}
