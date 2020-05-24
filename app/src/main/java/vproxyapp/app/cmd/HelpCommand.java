package vproxyapp.app.cmd;

import vproxyapp.app.Application;
import vproxyapp.app.cmd.handle.resource.SwitchHandle;
import vproxy.component.secure.SecurityGroup;
import vproxybase.util.Tuple;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HelpCommand {
    private HelpCommand() {
    }

    private static String withSpaces(String originStr, int totalCount) {
        if (originStr == null) {
            originStr = "";
        }
        StringBuilder sb = new StringBuilder(originStr);
        int len = totalCount - originStr.length();
        sb.append(" ".repeat(len));
        return sb.toString();
    }

    private static String withMaxLen(String originStr, int maxLen, int spaces) {
        if (originStr.length() < maxLen)
            return originStr;
        StringBuilder sb = new StringBuilder(originStr.substring(0, maxLen));
        int idx = maxLen;
        while (idx < originStr.length()) {
            if (originStr.charAt(idx - 1) != ' ' && originStr.charAt(idx) != ' ') {
                sb.append("-");
            }
            sb.append("\n");
            sb.append(" ".repeat(spaces));
            int end = idx + maxLen;
            if (end > originStr.length()) {
                end = originStr.length();
            }
            String slice = originStr.substring(idx, end);
            sb.append(slice);
            idx += maxLen;
        }
        return sb.toString();
    }

    public static String helpString() {
        int maxLenOfFullName = "man ...".length();
        {
            for (ActMan actMan : ActMan.values()) {
                int len = actMan.act.length();
                if (len > maxLenOfFullName)
                    maxLenOfFullName = len;
            }
            for (ResMan resMan : ResMan.values()) {
                int len = resMan.res.length();
                if (len > maxLenOfFullName)
                    maxLenOfFullName = len;
            }
            for (ParamMan paramMan : ParamMan.values()) {
                int len = paramMan.param.length();
                if (len > maxLenOfFullName)
                    maxLenOfFullName = len;
            }
            for (FlagMan flagMan : FlagMan.values()) {
                int len = flagMan.flag.length();
                if (len > maxLenOfFullName)
                    maxLenOfFullName = len;
            }
        }
        maxLenOfFullName += 4;
        int maxLenOfShortName = 0;
        {
            for (ActMan actMan : ActMan.values()) {
                int len = actMan.shortVer.length();
                if (len > maxLenOfShortName)
                    maxLenOfShortName = len;
            }
            for (ResMan resMan : ResMan.values()) {
                if (resMan.shortVer == null)
                    continue;
                int len = resMan.shortVer.length();
                if (len > maxLenOfShortName)
                    maxLenOfShortName = len;
            }
            for (ParamMan paramMan : ParamMan.values()) {
                if (paramMan.shortVer == null)
                    continue;
                int len = paramMan.shortVer.length();
                if (len > maxLenOfShortName)
                    maxLenOfShortName = len;
            }
            for (FlagMan flagMan : FlagMan.values()) {
                if (flagMan.shortVer == null)
                    continue;
                int len = flagMan.shortVer.length();
                if (len > maxLenOfShortName)
                    maxLenOfShortName = len;
            }
        }
        maxLenOfShortName += 4;

        int descrSpaces = 8 + maxLenOfFullName + maxLenOfShortName;
        int descrMaxLen = 40;

        StringBuilder sb = new StringBuilder();
        sb.append("vproxy:")
            .append("\n    System commands:")
            .append("\n        ")
            .append(withSpaces("help", maxLenOfFullName))
            .append(withSpaces("h", maxLenOfShortName))
            .append(withMaxLen("show this message", descrMaxLen, descrSpaces))
            .append("\n        ")
            .append(withSpaces("man", maxLenOfFullName))
            .append(withSpaces(null, maxLenOfShortName))
            .append(withMaxLen("show this message", descrMaxLen, descrSpaces))
            .append("\n        ")
            .append(withSpaces("man ...", maxLenOfFullName))
            .append(withSpaces(null, maxLenOfShortName))
            .append(withMaxLen("use `man action|resource|param_name` to get detailed doc." +
                    "use `man add-to|remove-from` to see info about `add ... to ...` or `remove ... from ...`",
                descrMaxLen, descrSpaces))
            .append(SystemCommand.systemCallHelpStr)
            .append("\n    Available actions:");
        for (ActMan actMan : ActMan.values()) {
            sb.append("\n        ")
                .append(withSpaces(actMan.act, maxLenOfFullName))
                .append(withSpaces(actMan.shortVer, maxLenOfShortName))
                .append(withMaxLen(actMan.descr, descrMaxLen, descrSpaces));
        }
        sb.append("\n    Available resources:");
        for (ResMan resMan : ResMan.values()) {
            sb.append("\n        ")
                .append(withSpaces(resMan.res, maxLenOfFullName))
                .append(withSpaces(resMan.shortVer, maxLenOfShortName))
                .append(withMaxLen(resMan.descr, descrMaxLen, descrSpaces));
        }
        sb.append("\n    Available params:");
        for (ParamMan paramMan : ParamMan.values()) {
            sb.append("\n        ")
                .append(withSpaces(paramMan.param, maxLenOfFullName))
                .append(withSpaces(paramMan.shortVer, maxLenOfShortName))
                .append(withMaxLen(paramMan.descr, descrMaxLen, descrSpaces));
        }
        sb.append("\n    Available flags:");
        for (FlagMan flagMan : FlagMan.values()) {
            sb.append("\n        ")
                .append(withSpaces(flagMan.flag, maxLenOfFullName))
                .append(withSpaces(flagMan.shortVer, maxLenOfShortName))
                .append(withMaxLen(flagMan.descr, descrMaxLen, descrSpaces));
        }
        return sb.toString();
    }

    public static String manLine(String line) {
        String[] arr = line.split(" ");
        if (arr.length == 2)
            return man(arr[1]);
        if (arr.length == 3)
            return man(arr[1], arr[2]);
        return "Invalid manual request: " + line;
    }

    public static String man(String item) {
        try {
            return man(getAct(item));
        } catch (IllegalArgumentException e1) {
            try {
                return man(getRes(item));
            } catch (IllegalArgumentException e2) {
                try {
                    return man(getParam(item));
                } catch (IllegalArgumentException e3) {
                    try {
                        return man(getFlag(item));
                    } catch (IllegalArgumentException e4) {
                        return "No manual entry for " + item;
                    }
                }
            }
        }
    }

    private static ActMan getAct(String s) {
        if (s.equals("add-to"))
            return ActMan.addto;
        if (s.equals("remove-from"))
            return ActMan.removefrom;
        for (ActMan actMan : ActMan.values()) {
            if (actMan.act.equals(s) || s.equals(actMan.shortVer))
                return actMan;
        }
        throw new IllegalArgumentException();
    }

    private static ResMan getRes(String s) {
        for (ResMan resMan : ResMan.values()) {
            if (resMan.res.equals(s) || s.equals(resMan.shortVer))
                return resMan;
        }
        throw new IllegalArgumentException();
    }

    private static ParamMan getParam(String s) {
        for (ParamMan paramMan : ParamMan.values()) {
            if (paramMan.param.equals(s) || s.equals(paramMan.shortVer))
                return paramMan;
        }
        throw new IllegalArgumentException();
    }

    private static FlagMan getFlag(String s) {
        for (FlagMan flagMan : FlagMan.values()) {
            if (flagMan.flag.equals(s) || s.equals(flagMan.shortVer))
                return flagMan;
        }
        throw new IllegalArgumentException();
    }

    private static String man(ActMan actMan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Action: ").append(actMan.act).append("\n\n");
        if (actMan.shortVer != null) {
            sb.append("Short version: ").append(actMan.shortVer).append("\n\n");
        }
        sb.append(actMan.descr);
        return sb.toString();
    }

    private static String man(ResMan resMan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resource: ").append(resMan.res).append("\n\n");
        if (resMan.shortVer != null) {
            sb.append("Short version: ").append(resMan.shortVer).append("\n\n");
        }
        sb.append(resMan.descr).append("\n\n");
        sb.append("Available actions for the resource:");
        for (ResActMan ram : resMan.acts) {
            sb.append("\n* ").append(ram.act.act);
        }
        return sb.toString();
    }

    private static String man(ParamMan paramMan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Param: ").append(paramMan.param).append("\n");
        if (paramMan.shortVer != null) {
            sb.append("Short version: ").append(paramMan.shortVer).append("\n");
        }
        sb.append(paramMan.descr);
        return sb.toString();
    }

    private static String man(FlagMan flagMan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Flag: ").append(flagMan.flag).append("\n");
        if (flagMan.shortVer != null)
            sb.append("Short version: ").append(flagMan.shortVer).append("\n");
        sb.append(flagMan.descr);
        return sb.toString();
    }

    public static String man(String resource, String action) {
        ResMan resMan;
        ActMan actMan;
        {
            try {
                resMan = getRes(resource);
            } catch (IllegalArgumentException e) {
                return "No resource named " + resource;
            }
            try {
                actMan = getAct(action);
            } catch (IllegalArgumentException e) {
                return "No action named " + action;
            }
        }
        ResActMan resActMan = null;
        for (ResActMan ram : resMan.acts) {
            if (ram.act == actMan) {
                resActMan = ram;
                break;
            }
        }
        if (resActMan == null) {
            return "Action " + action + " not available for resource " + resource;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Resource: ").append(resMan.res);
        if (resMan.shortVer != null) {
            sb.append(" (").append(resMan.shortVer).append(")");
        }
        sb.append("\n");
        sb.append("Action: ").append(actMan.act);
        if (actMan.shortVer != null) {
            sb.append(" (").append(actMan.shortVer).append(")");
        }
        sb.append("\n\n");
        sb.append(resActMan.descr);
        sb.append("\n\n");
        if (!resActMan.paramDescr.isEmpty()) {
            sb.append("Parameters:\n");
        }
        for (ResActParamMan rapm : resActMan.paramDescr) {
            if (rapm.optional) {
                sb.append("[optional] ");
            } else {
                sb.append("           ");
            }
            sb.append(rapm.param.param);
            if (rapm.param.shortVer != null) {
                sb.append(" (").append(rapm.param.shortVer).append(")");
            }
            sb.append(": ").append(rapm.descr);
            if (rapm.optional) {
                sb.append(" Default: ").append(rapm.defaultValue);
            }
            sb.append("\n");
        }
        sb.append("\n");
        if (!resActMan.flagDescr.isEmpty()) {
            sb.append("Flags:\n");
        }
        for (ResActFlagMan rafm : resActMan.flagDescr) {
            if (rafm.optional) {
                sb.append("[optional] ");
            } else {
                sb.append("           ");
            }
            if (rafm.optional && rafm.isDefault) {
                sb.append("[default] ");
            } else {
                sb.append("          ");
            }
            sb.append(rafm.flag.flag);
            if (rafm.flag.shortVer != null) {
                sb.append(" (").append(rafm.flag.shortVer).append(")");
            }
            sb.append(": ").append(rafm.descr).append("\n");
        }
        sb.append("\n");
        if (resActMan.note != null) {
            sb.append("Note: ").append(resActMan.note).append("\n\n");
        }
        sb.append("Example:\n");
        int idx = 0;
        for (Tuple<String, String> tup : resActMan.examples) {
            if (resActMan.examples.size() > 1) {
                sb.append("example").append(++idx).append(":\n");
            }
            sb.append("input>  ").append(tup.left).append("\n");
            if (tup.right.split("\n").length > 1) {
                sb.append("output>\n").append(tup.right);
            } else {
                sb.append("output> ").append(tup.right);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public enum ActMan {
        add("add", "a", "create a resource"),
        addto("add ... to ...", "a ... to ...", "attach a resource to another one"),
        list("list", "l", "list names, or retrieve count of some resources"),
        listdetail("list-detail", "L", "list detailed info of some resources"),
        update("update", "u", "modify a resource"),
        remove("remove", "r", "remove and destroy/stop a resource. If the resource is being used by another one, a warning will be returned and operation will be aborted"),
        forceremove("force-remove", "R", "remove and destroy/stop a resource, regardless of warnings"),
        removefrom("remove ... from ...", "r ... from ...", "detach a resource from another one"),
        ;
        public final String act;
        public final String shortVer;
        public final String descr;

        ActMan(String act, String shortVer, String descr) {
            this.act = act;
            this.shortVer = shortVer;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
        }
    }

    public enum ParamMan {
        acceptorelg("acceptor-elg", "aelg", "acceptor event loop group"),
        eventloopgroup("event-loop-group", "elg", "event loop group"),
        address("address", "addr", "ip address -> ip:port"),
        via("via", null, "the gateway ip for routing"),
        upstream("upstream", "ups", "upstream"),
        inbuffersize("in-buffer-size", null, "in buffer size"),
        outbuffersize("out-buffer-size", null, "out buffer size"),
        securitygroup("security-group", "secg", "security group"),
        timeout("timeout", null, "health check timeout"),
        period("period", null, "health check period"),
        up("up", null, "health check up times"),
        down("down", null, "health check down times"),
        method("method", "meth", "method to retrieve a server"),
        weight("weight", "w", "weight"),
        dft("default", null, "enum: allow or deny"),
        network("network", "net", "network: $network/$mask"),
        v4network("v4network", "v4net", "ipv4 network: $v4network/$mask"),
        v6network("v6network", "v6net", "ipv6 network: $v6network/$mask"),
        protocol("protocol", null, "" +
            "for tcp-lb: the application layer protocol, " +
            "for security-group: the transport layer protocol: tcp or udp"),
        annotations("annotations", "anno",
            "a string:string json representing metadata for the resource"),
        portrange("port-range", null, "an integer tuple $i,$j"),
        service("service", null, "service name"),
        zone("zone", null, "zone name"),
        nic("nic", null, "nic name"),
        iptype("ip-type", null, "ip type: v4 or v6"),
        port("port", null, "a port number"),
        certkey("cert-key", "ck", "cert-key resource"),
        cert("cert", null, "the certificate file path"),
        key("key", null, "the key file path"),
        ttl("ttl", null, "time to live"),
        mactabletimeout("mac-table-timeout", null, "timeout of mac table in a switch"),
        arptabletimeout("arp-table-timeout", null, "timeout of arp table in a switch"),
        pass("password", "pass", "password"),
        mac("mac", null, "mac address"),
        vni("vni", null, "vni number"),
        postscript("post-script", null, "the script to run after added"),
        ;
        public final String param;
        public final String shortVer;
        public final String descr;

        ParamMan(String param, String shortVer, String descr) {
            this.param = param;
            this.shortVer = shortVer;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
        }
    }

    public enum FlagMan {
        noipv4("noipv4", null, "do not use ipv4 address. Use the flag with param: address"),
        noipv6("noipv6", null, "do not use ipv6 address. Use the flag with param: address"),
        allownonbackend("allow-non-backend", null, "allow to access non backend endpoints"),
        denynonbackend("deny-non-backend", null, "only able to access backend endpoints"),
        noswitchflag("no-switch-flag", null, "do not add switch flag on vxlan packet"),
        ;
        public final String flag;
        public final String shortVer;
        public final String descr;

        FlagMan(String flag, String shortVer, String descr) {
            this.flag = flag;
            this.shortVer = shortVer;
            this.descr = descr;
        }
    }

    public enum ResMan {
        tcplb("tcp-lb", "tl", "TCP load balancer", Arrays.asList(
            new ResActMan(ActMan.add, "create a loadbalancer",
                Arrays.asList(
                    new ResActParamMan(ParamMan.acceptorelg, "choose an event loop group as the acceptor event loop group. can be the same as worker event loop group", Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group as the worker event loop group. can be the same as acceptor event loop group", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.address, "the bind address of the loadbalancer")
                    , new ResActParamMan(ParamMan.upstream, "used as the backend servers")
                    , new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.protocol, "the protocol used by tcp-lb. available options: tcp, http, h2, http/1.x, dubbo, framed-int32, or your customized protocol. See doc for more info", "tcp")
                    , new ResActParamMan(ParamMan.certkey, "the certificates and keys used by tcp-lb. Multiple cert-key(s) are separated with `,`")
                    , new ResActParamMan(ParamMan.securitygroup, "specify a security group for the lb", "allow any")
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 address 127.0.0.1:18080 upstream ups0 in-buffer-size 16384 out-buffer-size 16384",
                        "\"OK\""
                    )
                ))
            , new ResActMan(ActMan.list, "retrieve names of all tcp-loadbalancers",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "list tcp-lb",
                        "1) \"lb0\""
                    )
                ))
            , new ResActMan(ActMan.listdetail, "retrieve detailed info of all tcp-loadbalancers",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "list-detail tcp-lb",
                        "1) \"lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backend ups0 in-buffer-size 16384 out-buffer-size 16384 protocol tcp security-group secg0\""
                    )
                ))
            , new ResActMan(ActMan.update, "update in-buffer-size or out-buffer-size of an lb",
                Arrays.asList(
                    new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "not changed")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "not changed")
                    , new ResActParamMan(ParamMan.securitygroup, "the security group", "not changed")
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "update tcp-lb lb0 in-buffer-size 32768 out-buffer-size 32768",
                        "\"OK\""
                    )
                ))
            , new ResActMan(ActMan.remove, "remove and stop a tcp-loadbalancer. The already established connections won't be affected",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "remove tcp-lb lb0",
                        "\"OK\""
                    )
                ))
        )),
        socks5server("socks5-server", "socks5", "socks5 proxy server", Arrays.asList(
            new ResActMan(ActMan.add, "create a socks5 server",
                Arrays.asList(
                    new ResActParamMan(ParamMan.acceptorelg, "choose an event loop group as the acceptor event loop group. can be the same as worker event loop group", Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group as the worker event loop group. can be the same as acceptor event loop group", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.address, "the bind address of the loadbalancer")
                    , new ResActParamMan(ParamMan.upstream, "used as the backend servers")
                    , new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.securitygroup, "specify a security group for the socks5 server", "allow any")
                ),
                Arrays.asList(
                    new ResActFlagMan(FlagMan.allownonbackend, "allow to access non backend endpoints", false),
                    new ResActFlagMan(FlagMan.denynonbackend, "only enable backend endpoints", true)
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "add socks5-server s5 acceptor-elg acceptor event-loop-group worker address 127.0.0.1:18081 upstream backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0",
                        "\"OK\""
                    )
                ))
            , new ResActMan(ActMan.list, "retrieve names of socks5 servers",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "list socks5-server",
                        "1) \"s5\""
                    )
                ))
            , new ResActMan(ActMan.listdetail, "retrieve detailed info of socks5 servers",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "list-detail socks5-server",
                        "1) \"s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backend backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0\""
                    )
                ))
            , new ResActMan(ActMan.update, "update in-buffer-size or out-buffer-size of a socks5 server",
                Arrays.asList(
                    new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "not changed")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "not changed")
                    , new ResActParamMan(ParamMan.securitygroup, "the security group", "not changed")
                ),
                Arrays.asList(
                    new ResActFlagMan(FlagMan.allownonbackend, "allow to access non backend endpoints", false),
                    new ResActFlagMan(FlagMan.denynonbackend, "only enable backend endpoints", true)
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "update socks5-server s5 in-buffer-size 8192 out-buffer-size 8192",
                        "\"OK\""
                    )
                ))
            , new ResActMan(ActMan.remove, "remove a socks5 server",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "remove socks5-server s5",
                        "\"OK\""
                    )
                ))
        )),
        dns("dns-server", "dns", "dns server", Arrays.asList(
            new ResActMan(ActMan.add, "create a dns server",
                Arrays.asList(
                    new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group to run the dns server", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.address, "the bind address of the socks5 server")
                    , new ResActParamMan(ParamMan.upstream, "the domains to be resolved")
                    , new ResActParamMan(ParamMan.ttl, "the ttl of responded records", "0")
                    , new ResActParamMan(ParamMan.securitygroup, "specify a security group for the dns server", "allow any")
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "add dns-server dns0 address 127.0.0.1:53 upstream backend-groups ttl 0",
                        "\"OK\""
                    )
                ))
            , new ResActMan(ActMan.update, "update config of a dns server",
                Arrays.asList(
                    new ResActParamMan(ParamMan.ttl, "the ttl of responded records", "not changed")
                    , new ResActParamMan(ParamMan.securitygroup, "the security group", "not changed")
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "update dns-server dns0 ttl 60",
                        "\"OK\""
                    )
                ))
            , new ResActMan(ActMan.list, "retrieve names of dns servers",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "list dns-server",
                        "1) \"dns0\""
                    )
                ))
            , new ResActMan(ActMan.listdetail, "retrieve detailed info of dns servers",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "list-detail dns-server",
                        "1) \"dns0 -> event-loop-group worker bind 127.0.0.1:53 backend backend-groups security-group (allow-all)\""
                    )
                ))
            , new ResActMan(ActMan.remove, "remove a dns server",
                Collections.emptyList(),
                Collections.singletonList(
                    new Tuple<>(
                        "remove dns-server dns0",
                        "\"OK\""
                    )
                ))
        )),
        eventloopgroup("event-loop-group", "elg", "a group of event loops",
            Arrays.asList(
                new ResActMan(ActMan.add, "specify a name and create a event loop group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "add event-loop-group elg0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all event loop groups",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list event-loop-group",
                            "1) \"elg0\""
                        ),
                        new Tuple<>(
                            "list-detail event-loop-group",
                            "1) \"elg0\""
                        )
                    )),
                new ResActMan(ActMan.remove, "Remove a event loop group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove event-loop-group elg0",
                            "\"OK\""
                        )
                    ))
            )),
        upstream("upstream", "ups", "a resource containing multiple `server-group` resources",
            Arrays.asList(
                new ResActMan(ActMan.add, "specify a name and create an upstream resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "add upstream ups0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all upstream resources",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list upstream",
                            "1) \"ups0\""
                        ),
                        new Tuple<>(
                            "list-detail upstream",
                            "1) \"ups0\""
                        )
                    )),
                new ResActMan(ActMan.remove, "remove an upstream resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove upstream ups0",
                            "\"OK\""
                        )
                    ))
            )),
        servergroup("server-group", "sg", "a group of remote servers, which will run health check for all contained servers",
            Arrays.asList(
                new ResActMan(ActMan.add, "specify name, event loop, load balancing method, health check config and create a server group",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.timeout, "health check connect timeout (ms)"),
                        new ResActParamMan(ParamMan.period, "do check every `${period}` milliseconds"),
                        new ResActParamMan(ParamMan.up, "set server status to UP after succeeded for `${up}` times"),
                        new ResActParamMan(ParamMan.down, "set server status to DOWN after failed for `${down}` times"),
                        new ResActParamMan(ParamMan.protocol, "the protocol used for checking the servers, you may choose `tcp`, `none`", "tcp"),
                        new ResActParamMan(ParamMan.method, "loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`", "wrr"),
                        new ResActParamMan(ParamMan.annotations, "extra info for the server-group, such as host info, health check url. Must be a json and values must be strings", "{}"),
                        new ResActParamMan(ParamMan.eventloopgroup, "choose a event-loop-group for the server group. health check operations will be performed on the event loop group", Application.DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME)
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr elg elg0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.addto, "attach an existing server group into an `upstream` resource",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.weight, "the weight of group in this upstream resource", "10"),
                        new ResActParamMan(ParamMan.annotations, "extra info for the server-group inside upstream, such as host info. Must be a json and values must be strings", "{}")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add server-group sg0 to upstream ups0 weight 10",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all server group (s) on top level or in an upstream",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list server-group",
                            "1) \"sg0\""
                        ),
                        new Tuple<>(
                            "list server-group in upstream ups0",
                            "1) \"sg0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "Retrieve detailed info of all server group(s)",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list-detail server-group",
                            "1) \"sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {}\""
                        ),
                        new Tuple<>(
                            "list-detail server-group in upstream ups0",
                            "1) \"sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {} weight 10\""
                        )
                    )),
                new ResActMan(ActMan.update, "change health check config or load balancing algorithm.\n" +
                    "\n" +
                    "Param list is the same as add, but not all required.\n" +
                    "\n" +
                    "Also you can change the weight of a group in an upstream resource",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.timeout, "health check connect timeout (ms)", "not changed"),
                        new ResActParamMan(ParamMan.period, "do check every `${period}` milliseconds", "not changed"),
                        new ResActParamMan(ParamMan.up, "set server status to UP after succeeded for `${up}` times", "not changed"),
                        new ResActParamMan(ParamMan.down, "set server status to DOWN after failed for `${down}` times", "not changed"),
                        new ResActParamMan(ParamMan.protocol, "the protocol used for checking the servers, you may choose `tcp`, `none`. " +
                            "Note: this field will be set to `tcp` as default when updating other hc options", "not changed"),
                        new ResActParamMan(ParamMan.method, "loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`", "not changed"),
                        new ResActParamMan(ParamMan.weight, "the weight of group in the upstream resource (only available for server-group in upstream)", "not changed"),
                        new ResActParamMan(ParamMan.annotations, "annotation of the group itself, or the group in the upstream", "not changed")
                    ),
                    Arrays.asList(
                        new Tuple<>(
                            "update server-group sg0 timeout 500 period 600 up 3 down 2",
                            "\"OK\""
                        ),
                        new Tuple<>(
                            "update server-group sg0 method wlc",
                            "\"OK\""
                        ),
                        new Tuple<>(
                            "update server-group sg0 in upstream ups0 weight 5",
                            "\"OK\""
                        )
                    ), "all fields in health check config should be all specified if any one of them exists"),
                new ResActMan(ActMan.remove, "remove a server group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove server-group sg0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.removefrom, "detach the group from an `upstream` resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove server-group sg0 from upstream ups0",
                            "\"OK\""
                        )
                    ))
            )),
        eventloop("event-loop", "el", "event loop",
            Arrays.asList(
                new ResActMan(ActMan.addto, "specify a name, a event loop group, and create a new event loop in the specified group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "add event-loop el0 to elg elg0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all event loops in a event loop group",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list event-loop in event-loop-group elg0",
                            "1) \"el0\""
                        ),
                        new Tuple<>(
                            "list-detail event-loop in event-loop-group elg0",
                            "1) \"el0\""
                        )
                    )),
                new ResActMan(ActMan.removefrom, "remove a event loop from event loop group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove event-loop el0 from event-loop-group elg0",
                            "\"OK\""
                        )
                    ))
            )),
        server("server", "svr", "a remote endpoint",
            Arrays.asList(
                new ResActMan(ActMan.addto, "specify name, remote ip:port, weight, and attach the server into the server group",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.address, "remote address, ip:port"),
                        new ResActParamMan(ParamMan.weight, "weight of the server, which will be used by wrr, wlc and source algorithm", "10")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add server svr0 to server-group sg0 address 127.0.0.1:6379 weight 10",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all servers in a server group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list server in server-group sg0",
                            "1) \"svr0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "retrieve detailed info of all servers in a server group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail server in server-group sg0",
                            "1) \"svr0 -> connect-to 127.0.0.1:6379 weight 10 currently DOWN\""
                        )
                    )),
                new ResActMan(ActMan.update, "change weight of the server",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "update server svr0 in server-group sg0 weight 11",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.removefrom, "Remove a server from a server group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove server svr0 from server-group sg0",
                            "\"OK\""
                        )
                    ))
            )),
        securitygroup("security-group", "secg", "A white/black list, see `security-group-rule` for more info",
            Arrays.asList(
                new ResActMan(ActMan.add, "create a security group",
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.dft, "default: enum {allow, deny}\n" +
                            "if set to allow, then will allow connection if all rules not match\n" +
                            "if set to deny, then will deny connection if all rules not match")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add security-group secg0 default allow",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all security groups",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list security-group",
                            "1) \"secg0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "retrieve detailed info of all security groups",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail security-group",
                            "1) \"secg0 -> default allow\""
                        )
                    )),
                new ResActMan(ActMan.update, "update properties of a security group",
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.dft, "default: enum {allow, deny}")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "update security-group secg0 default deny",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.remove, "remove a security group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove security-group secg0",
                            "\"OK\""
                        )
                    ))
            )),
        securitygrouprule("security-group-rule", "secgr", "a rule containing protocol, source network, dest port range and whether to deny",
            Arrays.asList(
                new ResActMan(ActMan.add, "create a rule in the security group",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.network, "a cidr string for checking client ip"),
                        new ResActParamMan(ParamMan.protocol, "enum {TCP, UDP}"),
                        new ResActParamMan(ParamMan.portrange, "a tuple of integer for vproxy port, 0 <= first <= second <= 65535"),
                        new ResActParamMan(ParamMan.dft, "enum {allow, deny}\n" +
                            "if set to allow, then will allow the connection if matches\n" +
                            "if set to deny, then will deny the connection if matches")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add security-group-rule secgr0 to security-group secg0 network 10.127.0.0/16 protocol TCP port-range 22,22 default allow",
                            "\"OK\""
                        )
                    ), "network is for client (source ip), and port-range is for vproxy (destination port)"),
                new ResActMan(ActMan.list, "retrieve names of all rules in a security group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list security-group-rule in security-group secg0",
                            "1) \"secgr0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "retrieve detailed info of all rules in a security group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail security-group-rule in security-group secg0",
                            "1) \"secgr0 -> allow 10.127.0.0/16 protocol TCP port [22,33]\""
                        )
                    )),
                new ResActMan(ActMan.remove, "remove a rule from a security group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove security-group-rule secgr0 from security-group secg0",
                            "\"OK\""
                        )
                    ))
            )),
        certkey("cert-key", "ck", "Some certificates and one key",
            Arrays.asList(
                new ResActMan(ActMan.add, "Load certificates and key from file",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.cert, "the cert file path. Multiple files are separated with `,`"),
                        new ResActParamMan(ParamMan.key, "the key file path")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add cert-key vproxy.cassite.net cert ~/cert.pem key ~/key.pem",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "View loaded cert-key resources",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list cert-key",
                            "1) \"vproxy.cassite.net\""
                        )
                    )),
                new ResActMan(ActMan.remove, "Remove a cert-key resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove cert-key vproxy.cassite.net",
                            "\"OK\""
                        )
                    ))
            )),
        dnscache("dns-cache", null, "The dns record cache. It's a host -> ipv4List, ipv6List map. It can only be accessed from the (default) dns resolver",
            Arrays.asList(
                new ResActMan(ActMan.list, "count current cache",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list dns-cache in resolver (default)",
                            "(integer) 1"
                        )
                    )),
                new ResActMan(ActMan.listdetail, "list detailed info of dns cache.\n" +
                    "\n" +
                    "The return values are:\n" +
                    "\n" +
                    "host.\n" +
                    "ipv4 ip list.\n" +
                    "ipv6 ip list",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail dns-cache in resolver (default)",
                            "1) 1) \"localhost\"\n" +
                                "   2) 1) \"127.0.0.1\"\n" +
                                "   3) 1) \"[0000:0000:0000:0000:0000:0000:0000:0001]\""
                        )
                    )),
                new ResActMan(ActMan.forceremove, "specify the host and remove the dns cache",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "force-remove dns-cache localhost from resolver (default)",
                            "\"OK\""
                        )
                    ))
            )),
        serversock("server-sock", "ss", "represents a `ServerSocketChannel`, which binds an ip:port",
            Arrays.asList(
                new ResActMan(ActMan.list, "count server-socks",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list server-sock in el el0 in elg elg0",
                            "(integer) 1"
                        ),
                        new Tuple<>(
                            "list server-sock in tcp-lb lb0",
                            "(integer) 1"
                        ),
                        new Tuple<>(
                            "list server-sock in socks5-server s5",
                            "(integer) 1"
                        )
                    )),
                new ResActMan(ActMan.listdetail, "get info about bind servers",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list-detail server-sock in el el0 in elg elg0",
                            "1) \"127.0.0.1:6380\""
                        ),
                        new Tuple<>(
                            "list-detail server-sock in tcp-lb lb0",
                            "1) \"127.0.0.1:6380\""
                        ),
                        new Tuple<>(
                            "list-detail server-sock in socks5-server s5",
                            "1) \"127.0.0.1:18081\""
                        )
                    ))
            )),
        connection("connection", "conn", "represents a `SocketChannel`",
            Arrays.asList(
                new ResActMan(ActMan.list, "count connections",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list connection in el el0 in elg elg0",
                            "(integer) 2"
                        ),
                        new Tuple<>(
                            "list connection in tcp-lb lb0",
                            "(integer) 2"
                        ),
                        new Tuple<>(
                            "list connection in socks5-server s5",
                            "(integer) 2"
                        ),
                        new Tuple<>(
                            "list connection in server svr0 in sg sg0",
                            "(integer) 1"
                        )
                    )),
                new ResActMan(ActMan.listdetail, "get info about connections",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list-detail connection in el el0 in elg elg0",
                            "1) \"127.0.0.1:63537/127.0.0.1:6379\"\n" +
                                "2) \"127.0.0.1:63536/127.0.0.1:6380\""
                        ),
                        new Tuple<>(
                            "list-detail connection in tcp-lb lb0",
                            "1) \"127.0.0.1:63536/127.0.0.1:6380\"\n" +
                                "2) \"127.0.0.1:63537/127.0.0.1:6379\""
                        ),
                        new Tuple<>(
                            "list-detail connection in socks5-server s5",
                            "1) \"127.0.0.1:55981/127.0.0.1:18081\"\n" +
                                "2) \"127.0.0.1:55982/127.0.0.1:16666\""
                        ),
                        new Tuple<>(
                            "list-detail connection in server svr0 in sg sg0",
                            "1) \"127.0.0.1:63537/127.0.0.1:6379\""
                        )
                    )),
                new ResActMan(ActMan.forceremove, "close the connection, and if the connection is bond to a session, the session will be closed as well.\n" +
                    "\n" +
                    "Supports regexp pattern or plain string:\n" +
                    "\n" +
                    "* if the input starts with `/` and ends with `/`, then it's considered as a regexp.\n" +
                    "* otherwise it matches the full string",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "force-remove conn 127.0.0.1:57629/127.0.0.1:16666 from el worker2 in elg worker",
                            "\"OK\""
                        ),
                        new Tuple<>(
                            "force-remove conn /.*/ from el worker2 in elg worker",
                            "\"OK\""
                        )
                    ))
            )),
        session("session", "sess", "represents a tuple of connections: the connection from client to lb, and the connection from lb to backend server",
            Arrays.asList(
                new ResActMan(ActMan.list, "count loadbalancer sessions",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list session in tcp-lb lb0",
                            "(integer) 1"
                        ),
                        new Tuple<>(
                            "list session in socks5-server s5",
                            "(integer) 2"
                        )
                    )),
                new ResActMan(ActMan.listdetail, "get info about loadbalancer sessions",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list-detail session in tcp-lb lb0",
                            "1) 1) \"127.0.0.1:63536/127.0.0.1:6380\"\n" +
                                "   2) \"127.0.0.1:63537/127.0.0.1:6379\""
                        ),
                        new Tuple<>(
                            "list-detail session in socks5-server s5",
                            "1) 1) \"127.0.0.1:53589/127.0.0.1:18081\"\n" +
                                "   2) \"127.0.0.1:53591/127.0.0.1:16666\"\n" +
                                "2) 1) \"127.0.0.1:53590/127.0.0.1:18081\"\n" +
                                "   2) \"127.0.0.1:53592/127.0.0.1:16666\""
                        )
                    )),
                new ResActMan(ActMan.forceremove, "close a session from lb",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "force-remove sess 127.0.0.1:58713/127.0.0.1:18080->127.0.0.1:58714/127.0.0.1:16666 from tl lb0",
                            "\"OK\""
                        ),
                        new Tuple<>(
                            "force-remove sess /127.0.0.1:58713.*/ from tl lb0",
                            "\"OK\""
                        )
                    ))
            )),
        bytesin("bytes-in", "bin", "statistics: bytes flow from remote to local",
            Collections.singletonList(
                new ResActMan(ActMan.list, "get history total input bytes from a resource",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list bytes-in in server-sock 127.0.0.1:6380 in tl lb0",
                            "(integer) 45"
                        ),
                        new Tuple<>(
                            "list bytes-in in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0",
                            "(integer) 45"
                        ),
                        new Tuple<>(
                            "list bytes-in in server svr0 in sg sg0",
                            "(integer) 9767"
                        )
                    ))
            )),
        bytesout("bytes-out", "bout", "statistics: bytes flow from local to remote",
            Collections.singletonList(
                new ResActMan(ActMan.list, "get history total output bytes from a resource",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list bytes-out in server-sock 127.0.0.1:6380 in tl lb0",
                            "(integer) 9767"
                        ),
                        new Tuple<>(
                            "list bytes-out in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0",
                            "(integer) 9767"
                        ),
                        new Tuple<>(
                            "list bytes-out in server svr0 in sg sg0",
                            "(integer) 45"
                        )
                    ))
            )),
        acceptedconncount("accepted-conn-count", null, "Statistics: successfully accpeted connections",
            Collections.singletonList(
                new ResActMan(ActMan.list, "get history total accepted connection count",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list accepted-conn-count in server-sock 127.0.0.1:6380 in tl lb0",
                            "(integer) 2"
                        )
                    ))
            )),
        sw("switch", "sw", "a switch for vproxy wrapped vxlan packets",
            Arrays.asList(
                new ResActMan(ActMan.add, "create a switch",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.address, "binding udp address of the switch for wrapped vxlan packets"),
                        new ResActParamMan(ParamMan.mactabletimeout, "timeout for mac table (ms)", "" + SwitchHandle.MAC_TABLE_TIMEOUT),
                        new ResActParamMan(ParamMan.arptabletimeout, "timeout for arp table (ms)", "" + SwitchHandle.ARP_TABLE_TIMEOUT),
                        new ResActParamMan(ParamMan.eventloopgroup, "the event loop group used for handling packets", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME),
                        new ResActParamMan(ParamMan.securitygroup, "the security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected)", SecurityGroup.defaultName)
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add switch sw0 address 0.0.0.0:4789",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.update, "update a switch",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.mactabletimeout, "timeout for mac table (ms)", "not changed"),
                        new ResActParamMan(ParamMan.arptabletimeout, "timeout for arp table (ms)", "not changed"),
                        new ResActParamMan(ParamMan.securitygroup, "the security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected)", "not changed")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "update switch sw0 mac-table-timeout 60000 arp-table-timeout 120000",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "get names of switches",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list switch",
                            "1) \"sw0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "get detailed info of switches",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail switch",
                            "1) \"sw0\" -> event-loop-group worker bind 0.0.0.0:4789 password p@sSw0rD mac-table-timeout 300000 arp-table-timeout 14400000 bare-vxlan-access (allow-all)"
                        )
                    )),
                new ResActMan(ActMan.remove, "stop and remove a switch",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove switch sw0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.addto, "add a remote switch ref to a local switch. note: use list iface to see these remote switches",
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.address, "the remote switch address")
                    ),
                    Collections.singletonList(
                        new ResActFlagMan(FlagMan.noswitchflag, "do not add switch flag on vxlan packets sent through this iface", false)
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add switch sw1 to switch sw0 address 100.64.0.1:18472",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.removefrom, "remove a remote switch ref from a local switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove switch sw1 from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        vpc("vpc", null, "a private network",
            Arrays.asList(
                new ResActMan(ActMan.list, "list existing vpcs in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list vpc in switch sw0",
                        "1) (integer) 1314"
                    )
                )),
                new ResActMan(ActMan.listdetail, "list detailed info about vpcs in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail vpc in switch sw0",
                        "1) \"1314 -> v4network 172.16.0.0/16\""
                    )
                )),
                new ResActMan(ActMan.add, "create a vpc in a switch. the name should be vni of the vpc", Arrays.asList(
                    new ResActParamMan(ParamMan.v4network, "the ipv4 network allowed in this vpc"),
                    new ResActParamMan(ParamMan.v6network, "the ipv6 network allowed in this vpc", "not allowed")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add vpc 1314 in switch sw0 v4network 172.16.0.0/16",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.remove, "remove a vpc from a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remote vpc 1314 from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        iface("iface", null, "connected interfaces",
            Arrays.asList(
                new ResActMan(ActMan.list, "count currently connected interfaces in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list iface in switch sw0",
                        "(integer) 2"
                    )
                )),
                new ResActMan(ActMan.listdetail, "list current connected interfaces in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail iface in switch sw0",
                        "1) \"Iface(192.168.56.2:8472)\"\n" +
                            "2) \"Iface(100.64.0.4:8472)\""
                    )
                ))
            )),
        arp("arp", null, "arp and mac table entries",
            Arrays.asList(
                new ResActMan(ActMan.list, "count entries in a vpc", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list arp in vpc 1314 in switch sw0",
                        "(integer) 2"
                    )
                )),
                new ResActMan(ActMan.listdetail, "list arp and mac table entries in a vpc", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail arp in vpc 1314 in switch sw0",
                        "1) \"aa:92:96:2f:3b:7d        10.213.0.1             Iface(127.0.0.1:54042)        ARP-TTL:14390        MAC-TTL:299\"\n" +
                            "2) \"fa:e8:aa:6c:45:f4        10.213.0.2             Iface(127.0.0.1:57374)        ARP-TTL:14390        MAC-TTL:299\""
                    )
                ))
            )),
        user("user", null, "user in a switch",
            Arrays.asList(
                new ResActMan(ActMan.list, "list user names in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list user in switch sw0",
                        "1) \"hello\""
                    )
                )),
                new ResActMan(ActMan.listdetail, "list all user info in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail user in switch sw0",
                        "1) \"hello\" -> vni 1314"
                    )
                )),
                new ResActMan(ActMan.add, "add a user to a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.pass, "password of the user"),
                    new ResActParamMan(ParamMan.vni, "vni assigned for the user")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add user hello to switch sw0 vni 1314 password p@sSw0rD",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.remove, "remove a user from a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove user hello from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        tap("tap", null, "add/remove a tap device and bind it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. " +
            "Note: 1) use list iface to see these tap devices, 2) should set -Dvfd=posix or -Dvfd=windows",
            Arrays.asList(
                new ResActMan(ActMan.add, "add a user to a switch. Note: the result string is the name of the tap device because might be generated", Arrays.asList(
                    new ResActParamMan(ParamMan.vni, "vni assigned for the user"),
                    new ResActParamMan(ParamMan.postscript, "post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch)", "(empty)")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add tap tap%d to switch sw0 vni 1314",
                        "\"tap0\""
                    )
                )),
                new ResActMan(ActMan.remove, "remove and close a tap from a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove tap tap0 from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        usercli("user-client", "ucli", "user client of an encrypted tunnel to remote switch. Note: use list iface to see these clients",
            Arrays.asList(
                new ResActMan(ActMan.add, "add a user client to a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.pass, "password of the user"),
                    new ResActParamMan(ParamMan.vni, "vni which the user is assigned to"),
                    new ResActParamMan(ParamMan.address, "remote switch address to connect to")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add user-client hello to switch sw0 password p@sSw0rD vni 1314 address 192.168.77.1:18472",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.remove, "remove a user client from a switch", Collections.singletonList(
                    new ResActParamMan(ParamMan.address, "remote switch address the client connected to")
                ), Collections.singletonList(
                    new Tuple<>(
                        "remove user-client hello from switch sw0 address 192.168.77.1:18472",
                        "\"OK\""
                    )
                ))
            )),
        ip("ip", null, "synthetic ip in a vpc of a switch",
            Arrays.asList(
                new ResActMan(ActMan.list, "show synthetic ips in a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list ip in vpc 1314 in switch sw0",
                        "1) \"172.16.0.21\"\n" +
                            "2) \"[2001:0db8:0000:f101:0000:0000:0000:0002]\""
                    )
                )),
                new ResActMan(ActMan.listdetail, "show detailed info about synthetic ips in a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail ip in vpc 1314 in switch sw0",
                        "1) \"172.16.0.21 -> mac e2:8b:11:00:00:22\"\n" +
                            "2) \"[2001:0db8:0000:f101:0000:0000:0000:0002] -> mac e2:8b:11:00:00:33\""
                    )
                )),
                new ResActMan(ActMan.add, "add a synthetic ip to a vpc of a switch", Collections.singletonList(
                    new ResActParamMan(ParamMan.mac, "mac address that the ip assigned on")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add ip 172.16.0.21 to vpc 1314 in switch sw0 mac e2:8b:11:00:00:22",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.remove, "remove a synthetic ip from a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove ip 172.16.0.21 from vpc 1314 in switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        route("route", null, "route rules in a vpc of a switch",
            Arrays.asList(
                new ResActMan(ActMan.list, "show route rule names in a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list route in vpc 1314 in switch sw0",
                        "1) \"to172.17\"\n" +
                            "2) \"to2001:0db8:0000:f102\""
                    )
                )),
                new ResActMan(ActMan.listdetail, "show detailed info about route rules in a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail route in vpc 1314 in switch sw0",
                        "1) \"to172.17 -> network 172.17.0.0/24 vni 1315\"\n" +
                            "2) \"to2001:0db8:0000:f102 -> network [2001:0db8:0000:f102:0000:0000:0000:0000]/64 vni 1315\""
                    )
                )),
                new ResActMan(ActMan.add, "add a route to a vpc of a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.network, "network to be matched"),
                    new ResActParamMan(ParamMan.vni, "the vni to send packet to. only one of vni|via can be used"),
                    new ResActParamMan(ParamMan.via, "the address to forward the packet to. only one of via|vni can be used")
                ), Arrays.asList(
                    new Tuple<>(
                        "add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 vni 1315",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 via 172.16.0.1",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.remove, "remove a route rule from a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove route to172.17 from vpc 1314 in switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        ;
        public final String res;
        public final String shortVer;
        public final String descr;
        public final List<ResActMan> acts;

        ResMan(String res, String shortVer, String descr, List<ResActMan> acts) {
            this.res = res;
            this.shortVer = shortVer;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
            this.acts = Collections.unmodifiableList(acts);
        }
    }

    public static class ResActMan {
        public final ActMan act;
        public final String descr;
        public final List<ResActParamMan> paramDescr;
        public final List<ResActFlagMan> flagDescr;
        public final List<Tuple<String, String>> examples;
        public final String note;

        ResActMan(ActMan act, String descr, List<ResActParamMan> paramDescr, List<Tuple<String, String>> examples) {
            this(act, descr, paramDescr, Collections.emptyList(), examples);
        }

        ResActMan(ActMan act, String descr, List<ResActParamMan> paramDescr, List<ResActFlagMan> flagDescr, List<Tuple<String, String>> examples) {
            this(act, descr, paramDescr, flagDescr, examples, null);
        }

        ResActMan(ActMan act, String descr, List<ResActParamMan> paramDescr, List<Tuple<String, String>> examples, String note) {
            this(act, descr, paramDescr, Collections.emptyList(), examples, note);
        }

        ResActMan(ActMan act, String descr, List<ResActParamMan> paramDescr, List<ResActFlagMan> flagDescr, List<Tuple<String, String>> examples, String note) {
            this.act = act;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
            this.paramDescr = Collections.unmodifiableList(paramDescr);
            this.flagDescr = Collections.unmodifiableList(flagDescr);
            this.examples = examples;
            this.note = note;
        }
    }

    public static class ResActParamMan {
        public final ParamMan param;
        public final String descr;
        public final boolean optional;
        public final String defaultValue;

        public ResActParamMan(ParamMan param, String descr) {
            this.param = param;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
            this.optional = false;
            this.defaultValue = null;
        }

        public ResActParamMan(ParamMan param, String descr, String defaultValue) {
            this.param = param;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
            this.optional = true;
            this.defaultValue = defaultValue;
        }
    }

    public static class ResActFlagMan {
        public final FlagMan flag;
        public final String descr;
        public final boolean optional;
        public final boolean isDefault;

        public ResActFlagMan(FlagMan flag, String descr, boolean isDefault) {
            this.flag = flag;
            this.descr = descr.substring(0, 1).toUpperCase() + descr.substring(1) + ".";
            this.optional = true;
            this.isDefault = isDefault;
        }
    }
}
