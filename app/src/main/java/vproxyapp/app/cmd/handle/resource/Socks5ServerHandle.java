package vproxyapp.app.cmd.handle.resource;

import vfd.IPPort;
import vproxyapp.app.Application;
import vproxybase.Config;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Flag;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.handle.param.AddrHandle;
import vproxyapp.app.cmd.handle.param.InBufferSizeHandle;
import vproxyapp.app.cmd.handle.param.OutBufferSizeHandle;
import vproxyapp.app.cmd.handle.param.TimeoutHandle;
import vproxy.component.app.Socks5Server;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.util.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;

import java.util.LinkedList;
import java.util.List;

public class Socks5ServerHandle {
    private Socks5ServerHandle() {
    }

    public static void checkSocks5Server(Resource socks5) throws Exception {
        if (socks5.parentResource != null)
            throw new Exception(socks5.type.fullname + " is on top level");
    }

    @SuppressWarnings("Duplicates")
    public static void checkCreateSocks5Server(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.addr))
            throw new Exception("missing argument " + Param.addr.fullname);
        if (!cmd.args.containsKey(Param.ups))
            throw new Exception("missing argument " + Param.ups.fullname);

        AddrHandle.check(cmd);

        if (cmd.args.containsKey(Param.inbuffersize))
            InBufferSizeHandle.check(cmd);
        else
            cmd.args.put(Param.inbuffersize, "16384");

        if (cmd.args.containsKey(Param.outbuffersize))
            OutBufferSizeHandle.check(cmd);
        else
            cmd.args.put(Param.outbuffersize, "16384");

        if (cmd.args.containsKey(Param.timeout))
            TimeoutHandle.get(cmd);
    }

    public static void checkUpdateSocks5Server(Command cmd) throws Exception {
        if (cmd.args.containsKey(Param.inbuffersize))
            InBufferSizeHandle.check(cmd);

        if (cmd.args.containsKey(Param.outbuffersize))
            OutBufferSizeHandle.check(cmd);
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
        int inBufferSize = InBufferSizeHandle.get(cmd);
        int outBufferSize = OutBufferSizeHandle.get(cmd);
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

    public static void forceRemove(Command cmd) throws Exception {
        Application.get().socks5ServerHolder.removeAndStop(cmd.resource.alias);
    }

    public static void update(Command cmd) throws Exception {
        Socks5Server socks5 = get(cmd.resource);

        if (cmd.args.containsKey(Param.inbuffersize)) {
            socks5.setInBufferSize(InBufferSizeHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.outbuffersize)) {
            socks5.setOutBufferSize(OutBufferSizeHandle.get(cmd));
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
