package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.exception.NotFoundException;
import vproxy.util.Utils;
import vswitch.MacTable;
import vswitch.Switch;
import vswitch.Table;
import vswitch.util.Iface;
import vswitch.util.MacAddress;

import java.net.InetAddress;
import java.util.*;

public class ArpHandle {
    private ArpHandle() {
    }

    public static void checkArp(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.arp.fullname + " on top level");
        if (parent.type != ResourceType.vni) {
            if (parent.type == ResourceType.sw) {
                throw new Exception(parent.type.fullname + " does not directly contain " + ResourceType.arp.fullname + ", you have to specify vni first");
            }
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.arp.fullname);
        }

        VniHandle.checkVni(parent.parentResource);
        VniHandle.checkVniName(parent);
    }

    public static int count(Resource parent) throws Exception {
        return list(parent).size();
    }

    public static List<ArpEntry> list(Resource parent) throws Exception {
        String vniStr = parent.alias;
        int vni = Integer.parseInt(vniStr);
        Switch sw = Application.get().switchHolder.get(parent.parentResource.alias);
        var tables = sw.getTables().values();

        Table table = null;
        for (var tbl : tables) {
            if (tbl.vni == vni) {
                table = tbl;
                break;
            }
        }
        if (table == null) {
            throw new NotFoundException("vni", vniStr);
        }

        var macInArpEntries = new HashSet<MacAddress>();
        var arpEntries = table.arpTable.listEntries();
        var macEntries = table.macTable.listEntries();
        var macEntriesMap = new HashMap<MacAddress, MacTable.MacEntry>();
        for (var x : macEntries) {
            macEntriesMap.put(x.mac, x);
        }
        var result = new LinkedList<ArpEntry>();
        for (var a : arpEntries) {
            macInArpEntries.add(a.mac);
            var macEntry = macEntriesMap.get(a.mac);
            if (macEntry == null) {
                result.add(new ArpEntry(a.mac, a.ip, null, a.getTTL(), -1));
            } else {
                var iface = macEntry.iface;
                var ttl = macEntry.getTTL();
                result.add(new ArpEntry(a.mac, a.ip, iface, a.getTTL(), ttl));
            }
        }
        for (var m : macEntries) {
            if (macInArpEntries.contains(m.mac)) {
                continue;
            }
            result.add(new ArpEntry(m.mac, null, m.iface, -1, m.getTTL()));
        }
        result.sort((a, b) -> {
            // sort by mac
            int r = a.mac.toString().compareTo(b.mac.toString());
            if (r != 0) {
                return r;
            }
            // sort by ip, non-null in the front
            if (a.ip == null && b.ip != null) {
                return 1;
            }
            if (a.ip != null && b.ip == null) {
                return -1;
            }
            //noinspection ConstantConditions
            if (a.ip != null && b.ip != null) {
                r = Utils.ipStr(a.ip.getAddress()).compareTo(Utils.ipStr(b.ip.getAddress()));
                if (r != 0) {
                    return r;
                }
            }
            // sort by iface, non-null in the front
            if (a.iface == null && b.iface != null) {
                return 0;
            }
            if (a.iface != null && b.iface == null) {
                return -1;
            }
            //noinspection ConstantConditions
            if (a.iface != null && b.iface != null) {
                r = a.iface.toString().compareTo(b.iface.toString());
                //noinspection RedundantIfStatement
                if (r != 0) {
                    return r;
                }
            }
            // other not important
            return 0;
        });
        return result;
    }

    public static class ArpEntry {
        public final MacAddress mac;
        public final InetAddress ip;
        public final Iface iface;
        public final long arpTTL;
        public final long macTTL;

        public ArpEntry(MacAddress mac, InetAddress ip, Iface iface, long arpTTL, long macTTL) {
            this.mac = mac;
            this.ip = ip;
            this.iface = iface;
            this.arpTTL = arpTTL;
            this.macTTL = macTTL;
        }

        @Override
        public String toString() {
            final String split = "    ";
            StringBuilder sb = new StringBuilder();
            sb.append(mac);
            sb.append(split);
            if (ip == null) {
                sb.append(" ".repeat(41));
            } else {
                String ipStr = Utils.ipStr(ip);
                sb.append(ipStr);
                if (ipStr.length() < 41) {
                    int fix = 41 - ipStr.length();
                    sb.append(" ".repeat(fix));
                }
            }
            sb.append(split);
            if (iface == null) {
                sb.append(" ".repeat(46));
            } else {
                String ifaceStr = iface.toString();
                sb.append(ifaceStr);
                if (ifaceStr.length() < 46) {
                    int fix = 46 - ifaceStr.length();
                    sb.append(" ".repeat(fix));
                }
            }
            sb.append(split).append("ARP-TTL:").append(arpTTL / 1000);
            sb.append(split).append("MAC-TTL:").append(macTTL / 1000);
            return sb.toString();
        }
    }
}
