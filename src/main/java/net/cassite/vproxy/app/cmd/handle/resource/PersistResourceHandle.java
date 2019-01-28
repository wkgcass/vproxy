package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.exception.NotFoundException;

import java.util.ArrayList;
import java.util.List;

public class PersistResourceHandle {
    private PersistResourceHandle() {
    }

    public static void checkPersistParent(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.persist + " on top level");
        if (parent.type != ResourceType.tl)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.persist.fullname);
        TcpLBHandle.checkTcpLB(parent);
    }

    public static int count(Resource parent) throws NotFoundException {
        return TcpLBHandle.get(parent).persistMap.size();
    }

    public static List<PersistRef> detail(Resource parent) throws NotFoundException {
        List<TcpLB.Persist> list = new ArrayList<>(
            TcpLBHandle.get(parent).persistMap.values()
        );
        List<PersistRef> refs = new ArrayList<>(list.size());
        for (TcpLB.Persist p : list) {
            refs.add(new PersistRef(p));
        }
        return refs;
    }

    public static class PersistRef {
        public final TcpLB.Persist persist;

        public PersistRef(TcpLB.Persist persist) {
            this.persist = persist;
        }

        @Override
        public String toString() {
            return "client " + persist.clientAddress + " send to " + persist.connector;
        }
    }
}
