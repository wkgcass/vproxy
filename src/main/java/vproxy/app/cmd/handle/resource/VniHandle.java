package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vswitch.Switch;
import vswitch.Table;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VniHandle {
    private VniHandle() {
    }

    public static void checkVni(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.vni.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.vni.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static void checkVniName(Resource self) throws Exception {
        String vni = self.alias;
        try {
            Integer.parseInt(vni);
        } catch (NumberFormatException e) {
            throw new Exception("vni is not valid");
        }
    }

    public static Table get(Resource self) throws Exception {
        int vni = Integer.parseInt(self.alias);
        Switch sw = SwitchHandle.get(self.parentResource);
        return sw.getTable(vni);
    }

    public static void checkCreateVni(Command cmd) throws Exception {
        checkVni(cmd.prepositionResource);
        checkVniName(cmd.resource);
    }

    public static void add(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        sw.addTable(Integer.parseInt(cmd.resource.alias));
    }

    public static void forceRemove(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        sw.delTable(Integer.parseInt(cmd.resource.alias));
    }

    public static List<VniEntry> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        var tables = sw.getTables().values();

        List<VniEntry> ls = new ArrayList<>();
        for (var tbl : tables) {
            ls.add(new VniEntry(tbl.vni));
        }
        ls.sort(Comparator.comparingInt(a -> a.vni));
        return ls;
    }

    public static class VniEntry {
        public final int vni;

        public VniEntry(int vni) {
            this.vni = vni;
        }

        @Override
        public String toString() {
            return "" + vni;
        }
    }
}
