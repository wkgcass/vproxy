package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.IpParamHandle;
import vproxy.base.util.exception.NotFoundException;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.MacTable;
import vproxy.vswitch.Switch;
import vproxy.vswitch.VirtualNetwork;
import vproxy.vswitch.iface.Iface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class ArpHandle {
    private ArpHandle() {
    }

    public static void checkMacName(Resource self) throws Exception {
        try {
            new MacAddress(self.alias);
        } catch (RuntimeException e) {
            throw new Exception("arp name should be mac address");
        }
    }

    public static void add(Command cmd) throws Exception {
        MacAddress mac = new MacAddress(cmd.resource.alias);
        VirtualNetwork net = VpcHandle.get(cmd.prepositionResource);

        IP ip = null;
        if (cmd.args.containsKey(Param.ip)) {
            ip = IpParamHandle.get(cmd);
        }
        Iface iface = null;
        if (cmd.args.containsKey(Param.iface)) {
            iface = IfaceHandle.get(cmd.prepositionResource.parentResource, cmd.args.get(Param.iface));
        }
        if (ip != null) {
            net.arpTable.record(mac, ip, true);
        }
        if (iface != null) {
            net.macTable.record(mac, iface, true);
        }
    }

    public static int count(Resource parent) throws Exception {
        return list(parent).size();
    }

    public static List<ArpEntry> list(Resource parent) throws Exception {
        String vpcStr = parent.alias;
        int vpc = Integer.parseInt(vpcStr);
        Switch sw = Application.get().switchHolder.get(parent.parentResource.alias);
        var networks = sw.getNetworks().values();

        VirtualNetwork network = null;
        for (var net : networks) {
            if (net.vni == vpc) {
                network = net;
                break;
            }
        }
        if (network == null) {
            throw new NotFoundException("vpc", vpcStr);
        }

        var macInArpEntries = new HashSet<MacAddress>();
        var arpEntries = network.arpTable.listEntries();
        var macEntries = network.macTable.listEntries();
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

    public static void remove(Command cmd) throws Exception {
        MacAddress mac = new MacAddress(cmd.resource.alias);
        VirtualNetwork net = VpcHandle.get(cmd.prepositionResource);
        net.macTable.remove(mac);
        net.arpTable.remove(mac);
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
            return "" + (e.arpTTL == -1 ? -1 : e.arpTTL / 1000);
        }

        private String strForMacTTL(ArpEntry e) {
            return "" + (e.macTTL == -1 ? -1 : e.macTTL / 1000);
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
