package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.ResourceType;
import vproxy.base.util.exception.XException;
import vproxy.component.app.TcpLB;
import vproxy.component.ssl.CertKey;

import java.util.List;

public class CertKeyHandle {
    private CertKeyHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String[] certsPath = cmd.args.get(Param.cert).split(",");
        String keyPath = cmd.args.get(Param.key);
        Application.get().certKeyHolder.add(cmd.resource.alias, certsPath, keyPath);
    }

    public static List<String> names() {
        return Application.get().certKeyHolder.names();
    }

    public static void preRemoveCheck(Command cmd) throws Exception {
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

    public static void remove(Command cmd) throws Exception {
        Application.get().certKeyHolder.remove(cmd.resource.alias);
    }
}
