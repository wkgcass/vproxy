package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.AddrHandle;
import vproxy.app.app.cmd.handle.param.InBufferSizeHandle;
import vproxy.app.app.cmd.handle.param.OutBufferSizeHandle;
import vproxy.app.app.cmd.handle.param.TimeoutHandle;
import vproxy.base.Config;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.util.exception.NotFoundException;
import vproxy.component.app.Socks5Server;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.vfd.IPPort;

import java.util.LinkedList;
import java.util.List;

public class Socks5ServerHandle {
    private Socks5ServerHandle() {
    }

    public static Socks5Server get(Resource socks5) throws NotFoundException {
        return Application.get().socks5ServerHolder.get(socks5.alias);
    }

    public static List<String> names() {
        return Application.get().socks5ServerHolder.names();
    }

    public static List<Socks5ServerRef> details() throws Exception {
        List<Socks5ServerRef> result = new LinkedList<>();
        for (String name : names()) {
            result.add(new Socks5ServerRef(
                Application.get().socks5ServerHolder.get(name)
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
        int timeout;
        SecurityGroup secg;
        if (cmd.args.containsKey(Param.timeout)) {
            timeout = TimeoutHandle.get(cmd);
        } else {
            timeout = Config.tcpTimeout;
        }
        if (cmd.args.containsKey(Param.secg)) {
            secg = SecurityGroupHandle.get(cmd.args.get(Param.secg));
        } else {
            secg = SecurityGroup.allowAll();
        }
        Socks5Server server = Application.get().socks5ServerHolder.add(
            alias, acceptor, worker, addr, backend, timeout, inBufferSize, outBufferSize, secg
        );
        if (cmd.flags.contains(Flag.allownonbackend)) {
            server.allowNonBackend = true;
        } else if (cmd.flags.contains(Flag.denynonbackend)) {
            server.allowNonBackend = false;
        }
    }

    public static void remove(Command cmd) throws Exception {
        Application.get().socks5ServerHolder.removeAndStop(cmd.resource.alias);
    }

    public static void update(Command cmd) throws Exception {
        Socks5Server socks5 = get(cmd.resource);

        if (cmd.args.containsKey(Param.inbuffersize)) {
            socks5.setInBufferSize(InBufferSizeHandle.get(cmd, -1));
        }
        if (cmd.args.containsKey(Param.outbuffersize)) {
            socks5.setOutBufferSize(OutBufferSizeHandle.get(cmd, -1));
        }
        if (cmd.flags.contains(Flag.allownonbackend)) {
            socks5.allowNonBackend = true;
        } else if (cmd.flags.contains(Flag.denynonbackend)) {
            socks5.allowNonBackend = false;
        }
        if (cmd.args.containsKey(Param.timeout)) {
            socks5.setTimeout(TimeoutHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.secg)) {
            socks5.securityGroup = Application.get().securityGroupHolder.get(cmd.args.get(Param.secg));
        }
    }

    public static class Socks5ServerRef {
        public final Socks5Server socks5;

        public Socks5ServerRef(Socks5Server socks5Server) {
            this.socks5 = socks5Server;
        }

        @Override
        public String toString() {
            return socks5.alias + " -> acceptor " + socks5.acceptorGroup.alias + " worker " + socks5.workerGroup.alias
                + " bind " + socks5.bindAddress.getAddress().formatToIPString() + ":" + socks5.bindAddress.getPort()
                + " backend " + socks5.backend.alias
                + " timeout " + socks5.getTimeout()
                + " in-buffer-size " + socks5.getInBufferSize() + " out-buffer-size " + socks5.getOutBufferSize()
                + " security-group " + socks5.securityGroup.alias
                + " " + (socks5.allowNonBackend ? "allow-non-backend" : "deny-non-backend");
        }
    }
}
