package io.vproxy.app.app.cmd;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.handle.resource.SwitchHandle;
import io.vproxy.base.Config;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.xdp.BPFMode;
import io.vproxy.xdp.BPFObject;

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
                if (actMan.shortVer == null)
                    continue;
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
            .append("\n    commands:")
            .append("\n        ")
            .append(withSpaces("help", maxLenOfFullName))
            .append(withSpaces("h", maxLenOfShortName))
            .append(withMaxLen("show this message", descrMaxLen, descrSpaces))
            .append("\n        ")
            .append(withSpaces("System: help", maxLenOfFullName))
            .append(withSpaces(null, maxLenOfShortName))
            .append(withMaxLen("show system help message", descrMaxLen, descrSpaces))
            .append("\n        ")
            .append(withSpaces("man", maxLenOfFullName))
            .append(withSpaces(null, maxLenOfShortName))
            .append(withMaxLen("show this message", descrMaxLen, descrSpaces))
            .append("\n        ")
            .append(withSpaces("man ...", maxLenOfFullName))
            .append(withSpaces(null, maxLenOfShortName))
            .append(withMaxLen("use `man action|resource|param_name` to get detailed doc." +
                    "use `man add-to|remove-from` to see info about `add-to` or `remove-from`",
                descrMaxLen, descrSpaces))
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
        if (s.equals("remove-from") || s.equals("rm-from"))
            return ActMan.removefrom;
        for (ActMan actMan : ActMan.values()) {
            if (actMan.act.equals(s) || (actMan.shortVer != null && actMan.shortVer.equals(s)))
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
        add("add", null, "create a resource"),
        addto("add-to", null, "attach a resource to another one"),
        list("list", "ls", "list names, or retrieve count of some resources"),
        listdetail("list-detail", "ll", "list detailed info of some resources"),
        update("update", "mod", "modify a resource"),
        remove("remove", "rm", "remove and destroy/stop a resource. If the resource is being used by another one, a warning will be returned and operation will be aborted"),
        removefrom("remove-from", "rm-from", "detach a resource from another one"),
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
        weight("weight", null, "weight"),
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
        certkey("cert-key", "ck", "cert-key resource"),
        cert("cert", null, "the certificate file path"),
        key("key", null, "the key file path"),
        ttl("ttl", null, "time to live"),
        mactabletimeout("mac-table-timeout", null, "timeout of mac table in a switch"),
        arptabletimeout("arp-table-timeout", null, "timeout of arp table in a switch"),
        pass("password", "pass", "password"),
        mac("mac", null, "mac address"),
        routing("routing", null, "routing functions"),
        vni("vni", null, "vni number"),
        postscript("post-script", null, "the script to run after added"),
        mtu("mtu", null, "max transmission unit"),
        flood("flood", null, "flooding traffic"),
        csumrecalc("csum-recalc", null, "recalculate checksum of the received packet"),
        trace("trace", null, "trace packets in the switch"),
        offload("offload", null, "offload operations from java"),
        path("path", null, "file path"),
        program("program", "prog", "program name"),
        mode("mode", null, "mode"),
        umem("umem", null, "xdp umem"),
        nic("nic", null, "nic name"),
        queue("queue", null, "queue id"),
        xskmap("xsk-map", null, "queueId to xsk bpf map extracted from a bpfobject"),
        macmap("mac-map", null, "mac to ifindex bpf map extracted from a bpfobject"),
        rxringsize("rx-ring-size", null, "receiving ring size"),
        txringsize("tx-ring-size", null, "transmitting ring size"),
        chunks("chunks", null, "chunks"),
        fillringsize("fill-ring-size", null, "xdp umem fill ring size"),
        compringsize("comp-ring-size", null, "xdp umem comp ring size"),
        framesize("frame-size", null, "size of a frame"),
        xskmapkeyselector("xsk-map-key", null, "the method of " +
            "determining the key of the corresponding xsk when putting into a bpf map"),
        busypoll("busy-poll", null, "a number indicating whether to enable busy poll, " +
            "and may set the SO_BUSY_POLL_BUDGET as well"),
        ip("ip", null, "ip address"),
        iface("iface", null, "connected interface in switch"),
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
        force("force", null, "forcibly to do something"),
        zerocopy("zerocopy", null, "indicate to perform zerocopy operations"),
        rxgencsum("rx-gen-csum", null, "generate checksum before receiving the packet into vswitch"),
        enable("enable", null, "enable the resource"),
        disable("disable", null, "disable the resource"),
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
                    new ResActParamMan(ParamMan.address, "the bind address of the loadbalancer")
                    , new ResActParamMan(ParamMan.upstream, "used as the backend servers")
                    , new ResActParamMan(ParamMan.acceptorelg, "choose an event loop group as the acceptor event loop group. can be the same as worker event loop group", Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group as the worker event loop group. can be the same as acceptor event loop group", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.timeout, "idle timeout of connections in this lb instance", Config.tcpTimeout + " (ms)")
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
                    , new ResActParamMan(ParamMan.timeout, "idle timeout of connections in this lb instance", "not changed")
                    , new ResActParamMan(ParamMan.certkey, "the certificates and keys used by tcp-lb. Multiple cert-key(s) are separated with `,`", "not changed")
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
                    new ResActParamMan(ParamMan.address, "the bind address of the loadbalancer")
                    , new ResActParamMan(ParamMan.upstream, "used as the backend servers")
                    , new ResActParamMan(ParamMan.acceptorelg, "choose an event loop group as the acceptor event loop group. can be the same as worker event loop group", Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group as the worker event loop group. can be the same as acceptor event loop group", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.inbuffersize, "input buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.outbuffersize, "output buffer size", "16384 (bytes)")
                    , new ResActParamMan(ParamMan.timeout, "idle timeout of connections in this socks5 server instance", Config.tcpTimeout + " (ms)")
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
                    , new ResActParamMan(ParamMan.timeout, "idle timeout of connections in this socks5 server instance", "not changed")
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
                    new ResActParamMan(ParamMan.address, "the bind address of the socks5 server")
                    , new ResActParamMan(ParamMan.upstream, "the domains to be resolved")
                    , new ResActParamMan(ParamMan.eventloopgroup, "choose an event loop group to run the dns server", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)
                    , new ResActParamMan(ParamMan.ttl, "the ttl of responded records", "0")
                    , new ResActParamMan(ParamMan.securitygroup, "specify a security group for the dns server", "allow any")
                ),
                Collections.singletonList(
                    new Tuple<>(
                        "add dns-server dns0 address 127.0.0.1:53 upstream backend-groups ttl 0",
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
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.annotations, "extra info about the event loop group, e.g. use poll instead of epoll", "{}")
                    ),
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
                new ResActMan(ActMan.listdetail, "retrieve detailed info about all event loop groups",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail event-loop-group",
                            "1) \"elg0\" -> annotations {}"
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
                new ResActMan(ActMan.update, "change health check config or load balancing algorithm. " +
                    "Param list is the same as add, but not all required. " +
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
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.annotations, "extra info about the event loop, e.g. core affinity", "{}")
                    ),
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
                new ResActMan(ActMan.listdetail, "retrieve names of all event loops in a event loop group",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "list-detail event-loop in event-loop-group elg0",
                            "1) \"el0\" -> annotations {}"
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
                    Collections.singletonList(
                        new ResActParamMan(ParamMan.weight, "weight of the server, which will be used by wrr, wlc and source algorithm", "not changed")
                    ),
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
                new ResActMan(ActMan.addto, "create a rule in the security group",
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
                new ResActMan(ActMan.listdetail, "list detailed info of dns cache. " +
                    "The return values are: " +
                    "host. " +
                    "ipv4 ip list. " +
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
                new ResActMan(ActMan.remove, "specify the host and remove the dns cache",
                    Collections.emptyList(),
                    Collections.singletonList(
                        new Tuple<>(
                            "remove dns-cache localhost from resolver (default)",
                            "\"OK\""
                        )
                    ))
            )),
        sw("switch", "sw", "sdn virtual switch",
            Arrays.asList(
                new ResActMan(ActMan.add, "create a switch",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.address, "binding udp address of the switch for wrapped vxlan packets", "disable udp binding"),
                        new ResActParamMan(ParamMan.mactabletimeout, "timeout for mac table (ms)", "" + SwitchHandle.MAC_TABLE_TIMEOUT),
                        new ResActParamMan(ParamMan.arptabletimeout, "timeout for arp table (ms)", "" + SwitchHandle.ARP_TABLE_TIMEOUT),
                        new ResActParamMan(ParamMan.eventloopgroup, "the event loop group used for handling packets", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME),
                        new ResActParamMan(ParamMan.securitygroup, "the security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected)", SecurityGroup.defaultName),
                        new ResActParamMan(ParamMan.mtu, "default mtu setting for new connected ports, or -1 to ignore this config", "1500"),
                        new ResActParamMan(ParamMan.flood, "default flood setting for new connected ports", "allow"),
                        new ResActParamMan(ParamMan.csumrecalc, "default checksum recalculation type for new connected ports", "none")
                    ),
                    Arrays.asList(
                        new Tuple<>(
                            "add switch sw0",
                            "\"OK\""
                        ),
                        new Tuple<>(
                            "add switch sw0 address 0.0.0.0:4789",
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
                            "1) \"sw0\" -> event-loop-group worker bind 0.0.0.0:4789 mac-table-timeout 300000 arp-table-timeout 14400000 bare-vxlan-access (allow-all) trace 0"
                        )
                    )),
                new ResActMan(ActMan.update, "update a switch",
                    Arrays.asList(
                        new ResActParamMan(ParamMan.mactabletimeout, "timeout for mac table (ms)", "not changed"),
                        new ResActParamMan(ParamMan.arptabletimeout, "timeout for arp table (ms)", "not changed"),
                        new ResActParamMan(ParamMan.securitygroup, "the security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected)", "not changed"),
                        new ResActParamMan(ParamMan.mtu, "default mtu setting for new connected ports, updating it will not affect the existing ones. set to -1 for ignoring this config", "not changed"),
                        new ResActParamMan(ParamMan.flood, "default flood setting for new connected ports, updating it will not affect the existing ones", "not changed"),
                        new ResActParamMan(ParamMan.csumrecalc, "default checksum recalculation type for new connected ports, updating it will not affect the existing ones", "not changed"),
                        new ResActParamMan(ParamMan.trace, "the number of packets to trace in the switch", "not changed")
                    ),
                    Arrays.asList(
                        new Tuple<>(
                            "update switch sw0 mac-table-timeout 60000 arp-table-timeout 120000",
                            "\"OK\""
                        ),
                        new Tuple<>(
                            "update switch sw0 trace 10",
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
        trace("trace", null, "trace of packets in a switch",
            Arrays.asList(
                new ResActMan(ActMan.list, "show trace of packets in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list trace in switch sw0",
                        "## ... very long output ... ##"
                    )
                )),
                new ResActMan(ActMan.removefrom, "clear switch trace", Collections.emptyList(), Arrays.asList(
                    new Tuple<>(
                        "remove trace * from switch sw0",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "remove trace 0 from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        vpc("vpc", null, "a private network",
            Arrays.asList(
                new ResActMan(ActMan.addto, "create a vpc in a switch. the name should be vni of the vpc", Arrays.asList(
                    new ResActParamMan(ParamMan.v4network, "the ipv4 network allowed in this vpc"),
                    new ResActParamMan(ParamMan.v6network, "the ipv6 network allowed in this vpc", "not allowed"),
                    new ResActParamMan(ParamMan.annotations, "annotations of the vpc", "{}")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add vpc 1314 to switch sw0 v4network 172.16.0.0/16",
                        "\"OK\""
                    )
                )),
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
                new ResActMan(ActMan.removefrom, "remove a vpc from a switch", Collections.emptyList(), Collections.singletonList(
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
                )),
                new ResActMan(ActMan.update, "update interface config", Arrays.asList(
                    new ResActParamMan(ParamMan.mtu, "mtu of this interface, or -1 to ignore this config", "not changed"),
                    new ResActParamMan(ParamMan.flood, "whether to allow flooding traffic through this interface, allow or deny", "not changed"),
                    new ResActParamMan(ParamMan.csumrecalc, "whether to recalculate checksum for received packets of this interface", "not changed")
                ), Arrays.asList(
                    new ResActFlagMan(FlagMan.enable, "enable the iface", false),
                    new ResActFlagMan(FlagMan.disable, "disable the iface", false)
                ), Arrays.asList(
                    new Tuple<>(
                        "update iface tap:tap0 in switch sw0 mtu 9000 flood allow",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "update iface tun:utun9 in switch sw0 mtu 9000 flood allow",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "update iface remote:sw-x in switch sw0 mtu 1500 flood deny",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "update iface ucli:hello in switch sw0 mtu 1500 flood deny",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "update iface user:hello in switch sw0 mtu 1500 flood allow",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "update iface 10.0.0.1:8472 in switch sw0 mtu 1500 flood allow",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.removefrom, "remove interface from switch", Collections.emptyList(), Arrays.asList(
                    new Tuple<>(
                        "remove iface tap:tap0 from switch sw0",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "remove iface tun:utun9 from switch sw0",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "remove iface remote:sw-x from switch sw0",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "remove iface ucli:hello from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        arp("arp", null, "arp and mac table entries",
            Arrays.asList(
                new ResActMan(ActMan.addto, "create a mac entry and/or an arp entry", Arrays.asList(
                    new ResActParamMan(ParamMan.ip, "create arp entry with the specified ip", ""),
                    new ResActParamMan(ParamMan.iface, "create mac entry with the specified iface", "")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add arp 11:22:33:44:55:66 to vpc 1 in switch sw0 iface xdp:b1 ip 9.8.7.6",
                        "\"OK\""
                    )
                )),
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
                )),
                new ResActMan(ActMan.removefrom, "remove arp and mac table entries from a vpc", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove arp 11:22:33:44:55:66 from vpc 1 in switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        conntrack("conntrack", "ct", "connection tracking table entries", Arrays.asList(
            new ResActMan(ActMan.list, "count entries in a vpc", Collections.emptyList(), Collections.singletonList(
                new Tuple<>(
                    "list conntrack in vpc 1314 in switch sw0",
                    "(integer) 2"
                )
            )),
            new ResActMan(ActMan.listdetail, "list connection tracking table entries in a vpc", Collections.emptyList(), Collections.singletonList(
                new Tuple<>(
                    "list-detail conntrack in vpc 1314 in switch sw0",
                    "1) \"TCP ESTABLISHED remote=10.100.0.2:80    local=10.100.0.1:50014      --nat-> local=123.123.123.123:1234  remote=10.100.0.1:50014 TTL:431993\"\n" +
                        "2) \"TCP ESTABLISHED remote=10.100.0.1:50014 local=123.123.123.123:1234  --nat-> local=10.100.0.1:50014      remote=10.100.0.2:80    TTL:43199\""
                )
            ))
        )),
        user("user", null, "user in a switch",
            Arrays.asList(
                new ResActMan(ActMan.addto, "add a user to a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.pass, "password of the user"),
                    new ResActParamMan(ParamMan.vni, "vni assigned for the user"),
                    new ResActParamMan(ParamMan.mtu, "mtu for the user interface when the user is connected, or -1 to ignore this config", "mtu setting of the switch"),
                    new ResActParamMan(ParamMan.flood, "whether the user interface allows flooding traffic", "flood setting of the switch"),
                    new ResActParamMan(ParamMan.csumrecalc, "whether the user interface needs to recalculate checksum for received packets", "csum-recalc setting of the switch")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add user hello to switch sw0 vni 1314 password p@sSw0rD",
                        "\"OK\""
                    )
                )),
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
                new ResActMan(ActMan.update, "update user info in a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.mtu, "mtu for the user interface when the user is connected, updating it will not affect connected ones. -1 means ignoring this config", "not changed"),
                    new ResActParamMan(ParamMan.flood, "whether the user interface allows flooding traffic, updating it will not affect connected ones", "not changed"),
                    new ResActParamMan(ParamMan.csumrecalc, "whether the user interface needs to recalculate checksum for received packets, updating it will not affect connected ones", "not changed")
                ), Collections.singletonList(
                    new Tuple<>(
                        "update user hello in switch sw0 mtu 1500 flood allow",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.removefrom, "remove a user from a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove user hello from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        tap("tap", null, "add/remove a tap device and bind/detach it to/from a switch. " +
            "Note: should set -Dvfd=posix or -Dvfd=windows",
            Collections.singletonList(
                new ResActMan(ActMan.addto, "add a user to a switch. Note: the result string is the name of the tap device because might be generated", Arrays.asList(
                    new ResActParamMan(ParamMan.vni, "vni of the vpc which the tap device is attached to"),
                    new ResActParamMan(ParamMan.postscript, "post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch)", "(empty)")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add tap tap0 to switch sw0 vni 1314",
                        "\"OK\""
                    )
                ))
            )),
        tun("tun", null, "add/remove a tun device and bind/detach it to/from a switch. " +
            "Note: should set -Dvfd=posix",
            Collections.singletonList(
                new ResActMan(ActMan.addto, "add a user to a switch. Note: the result string is the name of the tun device because might be generated", Arrays.asList(
                    new ResActParamMan(ParamMan.vni, "vni of the vpc which the tun device is attached to"),
                    new ResActParamMan(ParamMan.mac, "mac address of this tun device. the switch requires l2 layer frames for handling packets"),
                    new ResActParamMan(ParamMan.postscript, "post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch)", "(empty)")
                ), Arrays.asList(
                    new Tuple<>(
                        "add tun tun0 to switch sw0 vni 1314",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "add tun utun9 to switch sw0 vni 1314",
                        "\"OK\""
                    )
                ))
            )),
        usercli("user-client", "ucli", "user client of an encrypted tunnel to remote switch. Note: use list iface to see these clients",
            Collections.singletonList(
                new ResActMan(ActMan.addto, "add a user client to a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.pass, "password of the user"),
                    new ResActParamMan(ParamMan.vni, "vni which the user is assigned to"),
                    new ResActParamMan(ParamMan.address, "remote switch address to connect to")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add user-client hello to switch sw0 password p@sSw0rD vni 1314 address 192.168.77.1:18472",
                        "\"OK\""
                    )
                ))
            )),
        xdp("xdp", null, "xdp socket, which is able to intercept packets from a net dev. " +
            "Note: 1) the name of the xdp iface is the nic name where this xdp handles, " +
            "2) use list iface to see the xdp sockets/interfaces, " +
            "3) should set -Dvfd=posix and make sure libvpxdp.so/libbpf.so/libelf.so on java.library.path, see build.gradle XDPPoc for example locations, " +
            "4) make sure your kernel supports xdp, recommend kernel version >= 5.10 (or at least 5.4). " +
            "See also `umem`, `bpf-object`. Check doc for more info",
            Collections.singletonList(
                new ResActMan(ActMan.addto, "add xdp socket into the switch", Arrays.asList(
                    new ResActParamMan(ParamMan.xskmap, "name of the bpf map to put the xdp socket into. The map should be defined in the maps section and must be a map of type BPF_MAP_TYPE_XSKMAP", BPFObject.DEFAULT_XSKS_MAP_NAME),
                    new ResActParamMan(ParamMan.macmap, "name of the bpf map to put the mac -> ifindex into. The map should be defined in the maps section and must be a map of {char[6] => int}", "automatically tries " + BPFObject.DEFAULT_MAC_MAP_NAME + " or ignore if failed to retrieve the map"),
                    new ResActParamMan(ParamMan.umem, "umem for the xdp socket to use. See `umem` for more info"),
                    new ResActParamMan(ParamMan.queue, "the queue index to bind to"),
                    new ResActParamMan(ParamMan.rxringsize, "rx ring size", "" + SwitchUtils.RX_TX_CHUNKS),
                    new ResActParamMan(ParamMan.txringsize, "tx ring size", "" + SwitchUtils.RX_TX_CHUNKS),
                    new ResActParamMan(ParamMan.mode, "mode of the xsk, enum: {SKB, DRIVER}, see doc for more info", "" + BPFMode.SKB),
                    new ResActParamMan(ParamMan.busypoll, "whether to enable busy poll, and set SO_BUSY_POLL_BUDGET. Set this option to 0 to disable busy poll", "0"),
                    new ResActParamMan(ParamMan.vni, "vni which the iface is assigned to"),
                    new ResActParamMan(ParamMan.xskmapkeyselector, "the method of " +
                        "determining the key of the corresponding xsk when putting into a bpf map", BPFMapKeySelectors.useQueueId.name()),
                    new ResActParamMan(ParamMan.offload, "offload mac switching to xdp program, which requires mac-map", "false")
                ), Arrays.asList(
                    new ResActFlagMan(FlagMan.zerocopy, "allow kernel to use zerocopy machanism", false),
                    new ResActFlagMan(FlagMan.rxgencsum, "generate checksum in native code before receiving the packet", false)
                ), Arrays.asList(
                    new Tuple<>(
                        "add xdp xdptut-4667 to switch sw0 xsk-map xsks_map umem umem0 queue 0 rx-ring-size 2048 tx-ring-size 2048 mode SKB vni 1 xsk-map-key useQueueId zerocopy",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "add xdp xdptut-4667 to switch sw0 umem umem0 queue 0 vni 1",
                        "\"OK\""
                    )
                ))
            )),
        vlanadaptor("vlan-adaptor", "vlan", "vlan adaptor which adds or removes 802.1q tag",
            Collections.singletonList(
                new ResActMan(ActMan.addto, "add vlan adaptor into the switch", Collections.singletonList(
                    new ResActParamMan(ParamMan.vni, "vni which the iface is assigned to", "same as vlan id")
                ), Arrays.asList(
                    new Tuple<>(
                        "add vlan 101@xdp:veth0 to switch sw0",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "add vlan 102@tap:tap1 to switch sw0 vni 202",
                        "\"OK\""
                    )
                ))
            )),
        ip("ip", null, "synthetic ip in a vpc of a switch",
            Arrays.asList(
                new ResActMan(ActMan.addto, "add a synthetic ip to a vpc of a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.mac, "mac address that the ip assigned on"),
                    new ResActParamMan(ParamMan.routing, "enable or disable routing functions on this ip, set to on/off", "on")
                ), Collections.singletonList(
                    new Tuple<>(
                        "add ip 172.16.0.21 to vpc 1314 in switch sw0 mac e2:8b:11:00:00:22",
                        "\"OK\""
                    )
                )),
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
                new ResActMan(ActMan.update, "modify a synthetic ip in a vpc of a switch", List.of(
                    new ResActParamMan(ParamMan.routing, "enable or disable routing functions on this ip, set to on/off", "not changed")
                ), Collections.singletonList(
                    new Tuple<>(
                        "update ip 172.16.0.21 in vpc 1314 in switch sw0 routing off",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.removefrom, "remove a synthetic ip from a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove ip 172.16.0.21 from vpc 1314 in switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        route("route", null, "route rules in a vpc of a switch",
            Arrays.asList(
                new ResActMan(ActMan.addto, "add a route to a vpc of a switch", Arrays.asList(
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
                new ResActMan(ActMan.removefrom, "remove a route rule from a vpc of a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove route to172.17 from vpc 1314 in switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        umem("umem", null, "umem for xdp sockets to use",
            Arrays.asList(
                new ResActMan(ActMan.addto, "add a umem to a switch", Arrays.asList(
                    new ResActParamMan(ParamMan.chunks, "how many chunks are there in this umem", "" + (SwitchUtils.RX_TX_CHUNKS * 2)),
                    new ResActParamMan(ParamMan.fillringsize, "size of the fill ring", "" + SwitchUtils.RX_TX_CHUNKS),
                    new ResActParamMan(ParamMan.compringsize, "size of the comp ring", "" + SwitchUtils.RX_TX_CHUNKS),
                    new ResActParamMan(ParamMan.framesize, "size of the frame, must be 2048 or 4096", "" + SwitchUtils.TOTAL_RCV_BUF_LEN / 2)
                ), Arrays.asList(
                    new Tuple<>(
                        "add umem umem0 to switch sw0",
                        "\"OK\""
                    ),
                    new Tuple<>(
                        "add umem umem1 to switch sw0 chunks 4096 fill-ring-size 2048 comp-ring-size 2048 frame-size 4096",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.list, "show umem names in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list umem in switch sw0",
                        "1) \"umem0\""
                    )
                )),
                new ResActMan(ActMan.listdetail, "show detailed info about umems in a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail umem in switch sw0",
                        "1) \"umem0 -> chunks 4096 fill-ring-size 2048 comp-ring-size 2048 frame-size 4096 currently valid current-refs [XDPSocket(xdptut-4667#0,fd=22,closed=false)]\""
                    )
                )),
                new ResActMan(ActMan.removefrom, "remove a umem from a switch", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove umem umem0 from switch sw0",
                        "\"OK\""
                    )
                ))
            )),
        bpfobj("bpf-object", "bpfobj", "the ebpf object attached to net dev. " +
            "Note that the name of the bpf-object is the nic name where ebpf program will be attached to",
            Arrays.asList(
                new ResActMan(ActMan.add, "load and attach ebpf to a net dev", Arrays.asList(
                    new ResActParamMan(ParamMan.path, "path to the ebpf program .o file"),
                    new ResActParamMan(ParamMan.program, "name of the program inside the ebpf object to be attached to the net dev"),
                    new ResActParamMan(ParamMan.mode, "attaching mode, enum: {SKB, DRIVER}", "" + BPFMode.SKB)
                ), Collections.singletonList(
                    new ResActFlagMan(FlagMan.force, "force to replace the old program attached to the dev", false)
                ), Collections.singletonList(
                    new Tuple<>(
                        "add bpf-object enp0s6 path /vproxy/vproxy/base/src/main/c/xdp/sample_kern.o program xdp_sock mode SKB force",
                        "\"OK\""
                    )
                )),
                new ResActMan(ActMan.list, "show bpf-object names (attached nic names)", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list bpf-object",
                        "1) \"enp0s6\"\n" +
                            "2) \"xdptut-4667\""
                    )
                )),
                new ResActMan(ActMan.listdetail, "show bpf-object detailed info", Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "list-detail bpf-object",
                        "1) \"enp0s6 -> path /vproxy/vproxy/base/src/main/c/xdp/sample_kern.o prog xdp_sock mode SKB\"\n" +
                            "2) \"xdptut-4667 -> path /vproxy/vproxy/base/src/main/c/xdp/sample_kern.o prog xdp_sock mode SKB\""
                    )
                )),
                new ResActMan(ActMan.removefrom, "remove bpf-object. The loaded program will be detached from the nic.",
                    Collections.emptyList(), Collections.singletonList(
                    new Tuple<>(
                        "remove bpf-object enp0s6",
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
