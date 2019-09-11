package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.app.TcpLB;
import vproxy.component.ssl.CertKey;

import java.util.List;

public class CertKeyHandle {
    private CertKeyHandle() {
    }

    public static void checkCertKey(Resource ck) throws Exception {
        if (ck.parentResource != null) {
            throw new Exception("cert-key is on top level");
        }
    }

    public static void checkAddCertKey(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.cert))
            throw new Exception("missing argument " + Param.cert.fullname);
        if (!cmd.args.containsKey(Param.key))
            throw new Exception("missing argument " + Param.key.fullname);
    }

    public static void add(Command cmd) throws Exception {
        checkAddCertKey(cmd);
        String[] certsPath = cmd.args.get(Param.cert).split(",");
        String keyPath = cmd.args.get(Param.key);
        Application.get().certKeyHolder.add(cmd.resource.alias, certsPath, keyPath);
    }

    public static List<String> names() {
        return Application.get().certKeyHolder.names();
    }

    public static void preCheck(Command cmd) throws Exception {
        String toRemove = cmd.resource.alias;
        List<String> names = Application.get().tcpLBHolder.names();
        for (String name : names) {
            TcpLB tcpLB = Application.get().tcpLBHolder.get(name);
            if (tcpLB.certKeys != null) {
                for (CertKey ck : tcpLB.certKeys) {
                    if (ck.alias.equals(toRemove)) {
                        throw new Exception(ResourceType.ck.fullname + " " + toRemove + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
                    }
                }
            }
        }
    }

    public static void forceRemove(Command cmd) throws Exception {
        Application.get().certKeyHolder.remove(cmd.resource.alias);
    }
}
