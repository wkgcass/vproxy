package net.cassite.vproxy.app.cmd;

import net.cassite.vproxy.util.Tuple;

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
        servergroups("server-groups", "sgs", "server groups"),
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
        protocol("protocol", null, "" +
            "for tcp-lb: the application layer protocol, " +
            "for security-group: the transport layer protocol: tcp or udp"),
        portrange("port-range", null, "an integer tuple $i,$j"),
        service("service", null, "service name"),
        zone("zone", null, "zone name"),
        tcplb("tcp-lb", "tl", "tcp loadbalancer"),
        servergroup("server-group", "sg", "a group of servers"),
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
                    new ResActParamMan(ParamMan.acceptorelg, "choose an event loop group as the acceptor event loop group. can be the same as worker event loop group")
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group as the worker event loop group. can be the same as acceptor event loop group")
                    , new ResActParamMan(ParamMan.address, "the bind address of the loadbalancer")
                    , new ResActParamMan(ParamMan.servergroups, "used as the backend servers")
                    , new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.protocol, "the protocol used by tcp-lb. available options: tcp, h2, or your customized protocol. See doc for more info", "tcp")
                    , new ResActParamMan(ParamMan.securitygroup, "specify a security group for the lb", "allow any")
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 address 127.0.0.1:18080 server-groups sgs0 in-buffer-size 16384 out-buffer-size 16384",
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
                        "1) \"lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backends sgs0 in-buffer-size 16384 out-buffer-size 16384 protocol tcp security-group secg0\""
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
                    new ResActParamMan(ParamMan.acceptorelg, "choose an event loop group as the acceptor event loop group. can be the same as worker event loop group")
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group as the worker event loop group. can be the same as acceptor event loop group")
                    , new ResActParamMan(ParamMan.address, "the bind address of the loadbalancer")
                    , new ResActParamMan(ParamMan.servergroups, "used as the backend servers")
                    , new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.securitygroup, "specify a security group for the lb", "allow any")
                ),
                Arrays.asList(
                    new ResActFlagMan(FlagMan.allownonbackend, "allow to access non backend endpoints", false),
                    new ResActFlagMan(FlagMan.denynonbackend, "only enable backend endpoints", true)
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "add socks5-server s5 acceptor-elg acceptor event-loop-group worker address 127.0.0.1:18081 server-groups backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0",
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
                        "1) \"s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backends backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0\""
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
        servergroups("server-groups", "sgs", "a resource containing multiple `server-group` resources",
            Arrays.asList(
                new ResActMan(ActMan.add, "specify a name and create a server-groups resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "add server-groups sgs0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all server-groups resources",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list server-groups",
                            "1) \"sgs0\""
                        ),
                        new Tuple<>(
                            "list-detail server-groups",
                            "1) \"sgs0\""
                        )
                    )),
                new ResActMan(ActMan.remove, "remove a server-groups resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove server-groups sgs0",
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
                        new ResActParamMan(ParamMan.method, "loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`", "wrr"),
                        new ResActParamMan(ParamMan.eventloopgroup, "choose a event-loop-group for the server group. health check operations will be performed on the event loop group")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr elg elg0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.addto, "attach an existing server group into a `server-groups` resource",
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.weight, "the weight of group in this server-groups resource")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add server-group sg0 to server-groups sgs0 weight 10",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "retrieve names of all server group (s) on top level or in a server-groups",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list server-group",
                            "1) \"sg0\""
                        ),
                        new Tuple<>(
                            "list server-group in server-groups sgs0",
                            "1) \"sg0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "Retrieve detailed info of all server group(s)",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list-detail server-group",
                            "1) \"sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0\""
                        ),
                        new Tuple<>(
                            "list-detail server-group in server-groups sgs0",
                            "1) \"sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 weight 10\""
                        )
                    )),
                new ResActMan(ActMan.update, "change health check config or load balancing algorithm.\n" +
                    "\n" +
                    "Param list is the same as add, but not all required.\n" +
                    "\n" +
                    "Also you can change the weight of a group in a server-groups resource",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.timeout, "health check connect timeout (ms)", "not changed"),
                        new ResActParamMan(ParamMan.period, "do check every `${period}` milliseconds", "not changed"),
                        new ResActParamMan(ParamMan.up, "set server status to UP after succeeded for `${up}` times", "not changed"),
                        new ResActParamMan(ParamMan.down, "set server status to DOWN after failed for `${down}` times", "not changed"),
                        new ResActParamMan(ParamMan.method, "loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`", "not changed"),
                        new ResActParamMan(ParamMan.weight, "the weight of group in this server-groups resource", "not changed")
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
                            "update server-group sg0 in server-groups sgs0 weight 5",
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
                new ResActMan(ActMan.removefrom, "detach the group grom a `server-groups` resource",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove server-group sg0 from server-groups sgs0",
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
                        new ResActParamMan(ParamMan.weight, "weight of the server, which will be used by wrr, wlc and source algorithm")
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
        bindserver("bind-server", "bs", "represents a `ServerSocketChannel`, which binds an ip:port",
            Arrays.asList(
                new ResActMan(ActMan.list, "count bind servers",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list bind-server in el el0 in elg elg0",
                            "(integer) 1"
                        ),
                        new Tuple<>(
                            "list bind-server in tcp-lb lb0",
                            "(integer) 1"
                        ),
                        new Tuple<>(
                            "list bind-server in socks5-server s5",
                            "(integer) 1"
                        )
                    )),
                new ResActMan(ActMan.listdetail, "get info about bind servers",
                    Collections.emptyList(),
                    Arrays.asList(
                        new Tuple<>(
                            "list-detail bind-server in el el0 in elg elg0",
                            "1) \"127.0.0.1:6380\""
                        ),
                        new Tuple<>(
                            "list-detail bind-server in tcp-lb lb0",
                            "1) \"127.0.0.1:6380\""
                        ),
                        new Tuple<>(
                            "list-detail bind-server in socks5-server s5",
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
                            "list bytes-in in bind-server 127.0.0.1:6380 in tl lb0",
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
                            "list bytes-out in bind-server 127.0.0.1:6380 in tl lb0",
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
                            "list accepted-conn-count in bind-server 127.0.0.1:6380 in tl lb0",
                            "(integer) 2"
                        )
                    ))
            )),
        slg("smart-lb-group", null, "A binding for an lb and a server-group with info from service mesh network",
            Arrays.asList(
                new ResActMan(ActMan.add, "create a new smart-lb-group binding",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.service, "the service watched by the auto lb"),
                        new ResActParamMan(ParamMan.zone, "the zone watched by the auto lb"),
                        new ResActParamMan(ParamMan.tcplb, "the tcp lb to bind"),
                        new ResActParamMan(ParamMan.servergroup, "the server group to bind")
                    ),
                    Collections.singletonList(
                        new Tuple<>(
                            "add smart-lb-group slg0 service myservice.com:443 zone z0 tcp-lb tl0 server-group sg0",
                            "\"OK\""
                        )
                    )),
                new ResActMan(ActMan.list, "get names of smart-lb-group bindings",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list smart-lb-group",
                            "1) \"slg0\""
                        )
                    )),
                new ResActMan(ActMan.listdetail, "get detailed info about smart-lb-group bindings",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail smart-lb-group",
                            "1) \"slg0 -> service myservice.com:443 zone z0 tcp-lb tl0 server-group sg0\""
                        )
                    )),
                new ResActMan(ActMan.remove, "remove the smart-lb-group binding",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove smart-lb-group slg0",
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
