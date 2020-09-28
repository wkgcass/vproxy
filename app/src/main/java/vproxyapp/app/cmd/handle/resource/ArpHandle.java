package vproxyapp.app.cmd.handle.resource;

import vfd.IP;
import vfd.MacAddress;
import vproxyapp.app.Application;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxybase.util.exception.NotFoundException;
import vswitch.MacTable;
import vswitch.Switch;
import vswitch.Table;
import vswitch.iface.Iface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class ArpHandle {
    private ArpHandle() {
    }

    public static void checkArpParent(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.arp.fullname + " on top level");
        if (parent.type != ResourceType.vpc) {
            if (parent.type == ResourceType.sw) {
                throw new Exception(parent.type.fullname + " does not directly contain " + ResourceType.arp.fullname + ", you have to specify vpc first");
            }
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.arp.fullname);
        }

        VpcHandle.checkVpc(parent);
    }

    public static int count(Resource parent) throws Exception {
        return list(parent).size();
    }

    public static List<ArpEntry> list(Resource parent) throws Exception {
        String vpcStr = parent.alias;
        int vpc = Integer.parseInt(vpcStr);
        Switch sw = Application.get().switchHolder.get(parent.parentResource.alias);
        var tables = sw.getTables().values();

        Table table = null;
        for (var tbl : tables) {
            if (tbl.vni == vpc) {
                table = tbl;
                break;
            }
        }
        if (table == null) {
            throw new NotFoundException("vpc", vpcStr);
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
                r = a.ip.formatToIPString().compareTo(b.ip.formatToIPString());
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
        public final IP ip;
        public final Iface iface;
        public final long arpTTL;
        public final long macTTL;

        public ArpEntry(MacAddress mac, IP ip, Iface iface, long arpTTL, long macTTL) {
            this.mac = mac;
            this.ip = ip;
            this.iface = iface;
            this.arpTTL = arpTTL;
            this.macTTL = macTTL;
        }

        private String strForIp(ArpEntry e) {
            if (e.ip == null) return "";
            return e.ip.formatToIPString();
        }

        private String strForIface(ArpEntry e) {
            if (e.iface == null) return "";
            return e.iface.toString();
        }

        private String strForArpTTL(ArpEntry e) {
            return "" + (e.arpTTL / 1000);
        }

        private String strForMacTTL(ArpEntry e) {
            return "" + (e.macTTL / 1000);
        }

        public String toString(List<ArpEntry> all) {
            int ipMaxWidth = 0;
            int ifaceMaxWidth = 0;
            int arpTTLMaxWidth = 0;
            int macTTLMaxWidth = 0;
            for (var e : all) {
                int l = strForIp(e).length();
                if (ipMaxWidth < l) {
                    ipMaxWidth = l;
                }
                l = strForIface(e).length();
                if (ifaceMaxWidth < l) {
                    ifaceMaxWidth = l;
                }
                l = strForArpTTL(e).length();
                if (arpTTLMaxWidth < l) {
                    arpTTLMaxWidth = l;
                }
                l = strForMacTTL(e).length();
                if (macTTLMaxWidth < l) {
                    macTTLMaxWidth = l;
                }
            }

            final String split = "    ";
            StringBuilder sb = new StringBuilder();
            sb.append(mac);
            sb.append(split);
            if (ip == null) {
                sb.append(" ".repeat(ipMaxWidth));
            } else {
                String ipStr = strForIp(this);
                sb.append(ipStr);
                if (ipStr.length() < ipMaxWidth) {
                    int fix = ipMaxWidth - ipStr.length();
                    sb.append(" ".repeat(fix));
                }
            }
            sb.append(split);
            if (iface == null) {
                sb.append(" ".repeat(ifaceMaxWidth));
            } else {
                String ifaceStr = strForIface(this);
                sb.append(ifaceStr);
                if (ifaceStr.length() < ifaceMaxWidth) {
                    int fix = ifaceMaxWidth - ifaceStr.length();
                    sb.append(" ".repeat(fix));
                }
            }
            String strArpTTL = strForArpTTL(this);
            sb.append(split).append("ARP-TTL:").append(strArpTTL);
            if (strArpTTL.length() < arpTTLMaxWidth) {
                sb.append(" ".repeat(arpTTLMaxWidth - strArpTTL.length()));
            }
            String strMacTTL = strForMacTTL(this);
            sb.append(split).append("MAC-TTL:").append(strMacTTL);
            return sb.toString();
        }
    }
}
