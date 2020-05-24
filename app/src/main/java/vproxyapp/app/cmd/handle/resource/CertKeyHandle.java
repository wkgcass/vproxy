package vproxyapp.app.cmd.handle.resource;

import vproxyapp.app.Application;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxy.component.app.TcpLB;
import vproxybase.util.exception.XException;
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
            if (tcpLB.getCertKeys() != null) {
                for (CertKey ck : tcpLB.getCertKeys()) {
                    if (ck.alias.equals(toRemove)) {
                        throw new XException(ResourceType.ck.fullname + " " + toRemove + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
                    }
                }
            }
        }
    }

    public static void forceRemove(Command cmd) throws Exception {
        Application.get().certKeyHolder.remove(cmd.resource.alias);
    }
}
