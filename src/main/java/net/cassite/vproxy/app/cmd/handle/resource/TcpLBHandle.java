package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.Config;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.handle.param.AddrHandle;
import net.cassite.vproxy.app.cmd.handle.param.InBufferSizeHandle;
import net.cassite.vproxy.app.cmd.handle.param.OutBufferSizeHandle;
import net.cassite.vproxy.app.cmd.handle.param.TimeoutHandle;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

public class TcpLBHandle {
    private TcpLBHandle() {
    }

    public static void checkTcpLB(Resource tcpLB) throws Exception {
        if (tcpLB.parentResource != null)
            throw new Exception(tcpLB.type.fullname + " is on top level");
    }

    @SuppressWarnings("Duplicates")
    public static void checkCreateTcpLB(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.elg))
            throw new Exception("missing argument " + Param.elg.fullname);
        if (!cmd.args.containsKey(Param.aelg))
            throw new Exception("missing argument " + Param.aelg.fullname);
        if (!cmd.args.containsKey(Param.addr))
            throw new Exception("missing argument " + Param.addr.fullname);
        if (!cmd.args.containsKey(Param.sgs))
            throw new Exception("missing argument " + Param.sgs.fullname);

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

    public static void checkUpdateTcpLB(Command cmd) throws Exception {
        if (cmd.args.containsKey(Param.inbuffersize))
            InBufferSizeHandle.check(cmd);

        if (cmd.args.containsKey(Param.outbuffersize))
            OutBufferSizeHandle.check(cmd);
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
        String alias = cmd.resource.alias;
        EventLoopGroup acceptor = Application.get().eventLoopGroupHolder.get(cmd.args.get(Param.aelg));
        EventLoopGroup worker = Application.get().eventLoopGroupHolder.get(cmd.args.get(Param.elg));
        InetSocketAddress addr = AddrHandle.get(cmd);
        ServerGroups backend = Application.get().serverGroupsHolder.get(cmd.args.get(Param.sgs));
        int inBufferSize = InBufferSizeHandle.get(cmd);
        int outBufferSize = OutBufferSizeHandle.get(cmd);
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
        Application.get().tcpLBHolder.add(
            alias, acceptor, worker, addr, backend, timeout, inBufferSize, outBufferSize, secg
        );
    }

    public static void forceRemove(Command cmd) throws Exception {
        Application.get().tcpLBHolder.removeAndStop(cmd.resource.alias);
    }

    public static void update(Command cmd) throws Exception {
        TcpLB tcpLB = get(cmd.resource);

        if (cmd.args.containsKey(Param.inbuffersize)) {
            tcpLB.setInBufferSize(InBufferSizeHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.outbuffersize)) {
            tcpLB.setOutBufferSize(OutBufferSizeHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.timeout)) {
            tcpLB.setTimeout(TimeoutHandle.get(cmd));
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
                + " bind " + Utils.ipStr(tcpLB.bindAddress.getAddress().getAddress()) + ":" + tcpLB.bindAddress.getPort()
                + " backends " + tcpLB.backends.alias
                + " timeout " + tcpLB.getTimeout()
                + " in buffer size " + tcpLB.getInBufferSize() + " out buffer size " + tcpLB.getOutBufferSize()
                + " security-group " + tcpLB.securityGroup.alias;
        }
    }
}
