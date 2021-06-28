package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.AddrHandle;
import vproxy.app.app.cmd.handle.param.InBufferSizeHandle;
import vproxy.app.app.cmd.handle.param.OutBufferSizeHandle;
import vproxy.app.app.cmd.handle.param.TimeoutHandle;
import vproxy.base.Config;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.exception.XException;
import vproxy.base.util.ringbuffer.ssl.VSSLContext;
import vproxy.component.app.TcpLB;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.Upstream;
import vproxy.vfd.IPPort;

import java.util.LinkedList;
import java.util.List;

public class TcpLBHandle {
    private TcpLBHandle() {
    }

    public static TcpLB get(Resource tcplb) throws NotFoundException {
        return Application.get().tcpLBHolder.get(tcplb.alias);
    }

    public static List<String> names() {
        return Application.get().tcpLBHolder.names();
    }

    public static List<TcpLBRef> details() throws Exception {
        List<TcpLBRef> result = new LinkedList<>();
        for (String name : names()) {
            result.add(new TcpLBRef(
                Application.get().tcpLBHolder.get(name)
            ));
        }
        return result;
    }

    @SuppressWarnings("Duplicates")
    public static void add(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.aelg)) {
            cmd.args.put(Param.aelg, Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME);
        }
        if (!cmd.args.containsKey(Param.elg)) {
            cmd.args.put(Param.elg, Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
        }

        String alias = cmd.resource.alias;
        EventLoopGroup acceptor = Application.get().eventLoopGroupHolder.get(cmd.args.get(Param.aelg));
        EventLoopGroup worker = Application.get().eventLoopGroupHolder.get(cmd.args.get(Param.elg));
        IPPort addr = AddrHandle.get(cmd);
        Upstream backend = Application.get().upstreamHolder.get(cmd.args.get(Param.ups));
        int inBufferSize = InBufferSizeHandle.get(cmd, 16384);
        int outBufferSize = OutBufferSizeHandle.get(cmd, 16384);
        String protocol = cmd.args.get(Param.protocol);
        if (protocol == null) protocol = "tcp";
        int timeout;
        SecurityGroup secg;
        if (cmd.args.containsKey(Param.secg)) {
            secg = SecurityGroupHandle.get(cmd.args.get(Param.secg));
        } else {
            secg = SecurityGroup.allowAll();
        }
        if (cmd.args.containsKey(Param.timeout)) {
            timeout = TimeoutHandle.get(cmd);
        } else {
            timeout = Config.tcpTimeout;
        }
        CertKey[] certKeys = null;
        if (cmd.args.containsKey(Param.ck)) {
            String[] cks = cmd.args.get(Param.ck).split(",");
            certKeys = new CertKey[cks.length];
            for (int i = 0; i < cks.length; ++i) {
                certKeys[i] = Application.get().certKeyHolder.get(cks[i]);
            }
        }
        Application.get().tcpLBHolder.add(
            alias, acceptor, worker, addr, backend, timeout, inBufferSize, outBufferSize, protocol, certKeys, secg
        );
    }

    public static void remove(Command cmd) throws Exception {
        Application.get().tcpLBHolder.removeAndStop(cmd.resource.alias);
    }

    public static void update(Command cmd) throws Exception {
        TcpLB tcpLB = get(cmd.resource);

        if (cmd.args.containsKey(Param.inbuffersize)) {
            tcpLB.setInBufferSize(InBufferSizeHandle.get(cmd, -1));
        }
        if (cmd.args.containsKey(Param.outbuffersize)) {
            tcpLB.setOutBufferSize(OutBufferSizeHandle.get(cmd, -1));
        }
        if (cmd.args.containsKey(Param.timeout)) {
            tcpLB.setTimeout(TimeoutHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.secg)) {
            tcpLB.securityGroup = Application.get().securityGroupHolder.get(cmd.args.get(Param.secg));
        }
        if (cmd.args.containsKey(Param.ck)) {
            if (tcpLB.getCertKeys() == null || tcpLB.getCertKeys().length == 0) {
                throw new XException("cannot configure the tcp-lb to use TLS when it's originally using plain TCP");
            }

            String[] cks = cmd.args.get(Param.ck).split(",");
            CertKey[] certKeys = new CertKey[cks.length];
            for (int i = 0; i < cks.length; ++i) {
                certKeys[i] = Application.get().certKeyHolder.get(cks[i]);
            }
            VSSLContext vsslContext = Application.get().tcpLBHolder.buildVSSLContext(certKeys);
            tcpLB.setCertKeys(vsslContext, certKeys);
        }
    }

    public static class TcpLBRef {
        public final TcpLB tcpLB;

        public TcpLBRef(TcpLB tcpLB) {
            this.tcpLB = tcpLB;
        }

        @Override
        public String toString() {
            return tcpLB.alias + " -> acceptor " + tcpLB.acceptorGroup.alias + " worker " + tcpLB.workerGroup.alias
                + " bind " + tcpLB.bindAddress.getAddress().formatToIPString() + ":" + tcpLB.bindAddress.getPort()
                + " backend " + tcpLB.backend.alias
                + " timeout " + tcpLB.getTimeout()
                + " in-buffer-size " + tcpLB.getInBufferSize() + " out-buffer-size " + tcpLB.getOutBufferSize()
                + " protocol " + tcpLB.protocol
                + " security-group " + tcpLB.securityGroup.alias;
        }
    }
}
