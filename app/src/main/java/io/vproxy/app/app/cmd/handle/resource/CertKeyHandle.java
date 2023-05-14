package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.exception.XException;
import io.vproxy.component.app.TcpLB;
import io.vproxy.component.ssl.CertKey;

import java.util.ArrayList;
import java.util.Base64;
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

    public static List<CertKeyRef> detail() throws Exception {
        var names = names();
        var res = new ArrayList<CertKeyRef>();
        for (var name : names) {
            var ck = Application.get().certKeyHolder.get(name);
            res.add(new CertKeyRef(ck));
        }
        return res;
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

    public static class CertKeyRef {
        private final CertKey ck;
        private final String keySHA1;

        public CertKeyRef(CertKey ck) {
            this.ck = ck;
            keySHA1 = Base64.getEncoder().encodeToString(Utils.sha1(ck.key.getBytes()));
        }

        @Override
        public String toString() {
            var certsStr = new StringBuilder();
            var isFirst = true;
            for (var path : ck.certPaths) {
                if (isFirst) isFirst = false;
                else certsStr.append(",");
                certsStr.append(path);
            }
            return ck.alias + " ->"
                + " certs " + certsStr
                + " key " + ck.keyPath
                + " keySHA1 " + keySHA1;
        }
    }
}
