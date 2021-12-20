package io.vproxy.vproxyx.websocks;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.promise.Promise;
import io.vproxy.dep.vjson.CharStream;
import io.vproxy.dep.vjson.JSON;
import io.vproxy.dep.vjson.cs.LineColCharStream;
import io.vproxy.dep.vjson.deserializer.DeserializeParserListener;
import io.vproxy.dep.vjson.parser.ParserMode;
import io.vproxy.dep.vjson.parser.ParserOptions;
import io.vproxy.dep.vjson.parser.ParserUtils;
import io.vproxy.dep.vjson.util.ObjectBuilder;
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigLoader {
    private int socks5ListenPort = 0;
    private int httpConnectListenPort = 0;
    private int ssListenPort = 0;
    private String ssPassword = "";
    private int dnsListenPort = 0;
    private boolean gateway = false;
    private int pacServerPort;
    private boolean udpOverTcpEnabled;
    private String udpOverTcpNic;
    private final Map<String, ServerList> servers = new HashMap<>();
    private final Map<String, List<DomainChecker>> domains = new HashMap<>();
    private final Map<String, List<DomainChecker>> proxyResolves = new HashMap<>();
    private final Map<String, List<DomainChecker>> noProxyDomains = new HashMap<>();
    private final List<DomainChecker> httpsSniErasureDomains = new ArrayList<>();
    private String autoSignCert;
    private String autoSignKey;
    private File autoSignWorkingDirectory;
    private final List<List<String>> httpsSniErasureCertKeyFiles = new ArrayList<>();
    private boolean directRelay = false;
    private Network directRelayIpRange = null;
    private IPPort directRelayListen = null;
    private int directRelayIpBondTimeout = 10 * 60_000;
    private String user;
    private String pass;
    private String cacertsPath;
    private String cacertsPswd;
    private boolean verifyCert = true;
    private boolean strictMode = false;
    private int poolSize = 10;
    private boolean noHealthCheck = false;

    public ConfigLoader() {
    }

    public int getSocks5ListenPort() {
        return socks5ListenPort;
    }

    public int getHttpConnectListenPort() {
        return httpConnectListenPort;
    }

    public int getSsListenPort() {
        return ssListenPort;
    }

    public String getSsPassword() {
        return ssPassword;
    }

    public int getDnsListenPort() {
        return dnsListenPort;
    }

    public boolean isGateway() {
        return gateway;
    }

    public int getPacServerPort() {
        return pacServerPort;
    }

    public boolean isUdpOverTcpEnabled() {
        return udpOverTcpEnabled;
    }

    public String getUdpOverTcpNic() {
        return udpOverTcpNic;
    }

    public Map<String, ServerList> getServers() {
        return servers;
    }

    private static LinkedHashMap<String, List<DomainChecker>> utilGetAlias2DomainCheckerListMap(
        Map<String, List<DomainChecker>> map
    ) {
        LinkedHashMap<String, List<DomainChecker>> ret = new LinkedHashMap<>();
        for (String key : map.keySet()) {
            if (!key.equals("DEFAULT")) {
                ret.put(key, map.get(key));
            }
        }
        // put DEFAULT to the last
        if (map.containsKey("DEFAULT")) {
            ret.put("DEFAULT", map.get("DEFAULT"));
        }
        return ret;
    }

    public LinkedHashMap<String, List<DomainChecker>> getDomains() {
        return utilGetAlias2DomainCheckerListMap(domains);
    }

    public LinkedHashMap<String, List<DomainChecker>> getProxyResolves() {
        return utilGetAlias2DomainCheckerListMap(proxyResolves);
    }

    public LinkedHashMap<String, List<DomainChecker>> getNoProxyDomains() {
        return utilGetAlias2DomainCheckerListMap(noProxyDomains);
    }

    public boolean isDirectRelay() {
        return directRelay;
    }

    public IPPort getDirectRelayListen() {
        return directRelayListen;
    }

    public Network getDirectRelayIpRange() {
        return directRelayIpRange;
    }

    public int getDirectRelayIpBondTimeout() {
        return directRelayIpBondTimeout;
    }

    public List<DomainChecker> getHttpsSniErasureDomains() {
        return httpsSniErasureDomains;
    }

    public String getAutoSignCert() {
        return autoSignCert;
    }

    public String getAutoSignKey() {
        return autoSignKey;
    }

    public File getAutoSignWorkingDirectory() {
        return autoSignWorkingDirectory;
    }

    public List<List<String>> getHttpsSniErasureCertKeyFiles() {
        return httpsSniErasureCertKeyFiles;
    }

    public String getUser() {
        return user;
    }

    public String getPass() {
        return pass;
    }

    public String getCacertsPath() {
        return cacertsPath;
    }

    public String getCacertsPswd() {
        return cacertsPswd;
    }

    public boolean isVerifyCert() {
        return verifyCert;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public boolean isNoHealthCheck() {
        return noHealthCheck;
    }

    private ServerList getGroup(String alias) {
        if (alias == null) {
            alias = "DEFAULT";
        }
        if (servers.containsKey(alias))
            return servers.get(alias);
        ServerList serverList = new ServerList();
        servers.put(alias, serverList);
        return serverList;
    }

    private static List<DomainChecker> utilGetDomainCheckerList(
        String alias,
        Map<String, List<DomainChecker>> domains
    ) {
        if (alias == null) {
            alias = "DEFAULT";
        }
        if (domains.containsKey(alias))
            return domains.get(alias);
        List<DomainChecker> checkers = new LinkedList<>();
        domains.put(alias, checkers);
        return checkers;
    }

    private List<DomainChecker> getDomainList(String alias) {
        return utilGetDomainCheckerList(alias, domains);
    }

    private List<DomainChecker> getProxyResolveList(String alias) {
        return utilGetDomainCheckerList(alias, proxyResolves);
    }

    private List<DomainChecker> getNoProxyDomainList(String alias) {
        return utilGetDomainCheckerList(alias, noProxyDomains);
    }

    @SuppressWarnings("ConstantConditions")
    public void load(String fileName) throws Exception {
        String content = Files.readString(Path.of(fileName));
        var listener = new DeserializeParserListener<>(VPWSAgentConfig.Companion.getRule());
        ParserUtils.buildFrom(
            new LineColCharStream(CharStream.from(content), fileName), ParserOptions.allFeatures().setListener(listener)
                .setMode(ParserMode.JAVA_OBJECT)
                .setNullArraysAndObjects(true)
        );
        var config = listener.get();
        { // indent for git diff
            { // indent for git diff
                { // indent for git diff
                    socks5ListenPort = config.getAgent().getSocks5Listen();
                    if (socks5ListenPort != 0 && (socks5ListenPort < 1 || socks5ListenPort > 65535)) {
                        throw new Exception("invalid agent.listen, port number out of range");
                    }
                    httpConnectListenPort = config.getAgent().getHttpConnectListen();
                    if (httpConnectListenPort != 0 && (httpConnectListenPort < 1 || httpConnectListenPort > 65535)) {
                        throw new Exception("invalid agent.httpconnect.listen, port number out of range");
                    }
                    ssListenPort = config.getAgent().getSsListen();
                    if (ssListenPort != 0 && (ssListenPort < 1 || ssListenPort > 65535)) {
                        throw new Exception("invalid agent.ss.listen, port number out of range");
                    }
                    ssPassword = config.getAgent().getSsPassword();
                    dnsListenPort = config.getAgent().getDnsListen();
                    if (dnsListenPort != 0 && (dnsListenPort < 1 || dnsListenPort > 65535)) {
                        throw new Exception("invalid agent.dns.listen, port number out of range");
                    }
                }
                gateway = config.getAgent().getGateway();
                directRelay = config.getAgent().getDirectRelay().getEnabled();
                {
                    var val = config.getAgent().getDirectRelay().getIpRange();
                    if (!Network.validNetworkStr(val)) {
                        throw new Exception("invalid network in agent.direct-relay.ip-range: " + val);
                    }
                    directRelayIpRange = Network.from(val);
                }
                {
                    var val = config.getAgent().getDirectRelay().getListen();
                    if (!IPPort.validL4AddrStr(val)) {
                        throw new Exception("invalid binding address in agent.direct-relay.listen: " + val);
                    }
                    String host = val.substring(0, val.lastIndexOf(":"));
                    int port = Integer.parseInt(val.substring(val.lastIndexOf(":") + 1));
                    directRelayListen = new IPPort(IP.from(host), port);
                }
                directRelayIpBondTimeout = config.getAgent().getDirectRelay().getIpBondTimeout() * 60 * 1000;
                {
                    var auth = config.getProxy().getAuth();
                    String[] userpass = auth.split(":");
                    if (userpass.length != 2)
                        throw new Exception("invalid proxy.server.auth: " + auth);
                    user = userpass[0].trim();
                    if (user.isEmpty())
                        throw new Exception("invalid proxy.server.auth: user is empty");
                    pass = userpass[1].trim();
                    if (pass.isEmpty())
                        throw new Exception("invalid proxy.server.auth: pass is empty");
                }
                noHealthCheck = !config.getProxy().getHc();
                {
                    cacertsPath = config.getAgent().getCacertsPath();
                    if (cacertsPath.isBlank())
                        throw new Exception("cacert path not specified");
                }
                {
                    cacertsPswd = config.getAgent().getCacertsPswd();
                    if (cacertsPswd.isBlank())
                        throw new Exception("cacert path not specified");
                }
                verifyCert = config.getAgent().getCertVerify();
                strictMode = config.getAgent().getStrict();
                poolSize = config.getAgent().getPool();
                pacServerPort = config.getAgent().getGatewayPacListen();
                udpOverTcpEnabled = config.getAgent().getUot().getEnabled();
                udpOverTcpNic = config.getAgent().getUot().getNic();
                {
                    var args = config.getAgent().getTlsSniErasure().getCertKeyAutoSign();
                    if (args.size() != 2 && args.size() != 3) {
                        throw new Exception("agent.tls-sni-erasure.cert-key.auto-sign should take exactly two arguments");
                    }
                    autoSignCert = Utils.filename(args.get(0));
                    autoSignKey = Utils.filename(args.get(1));
                    if (!new File(autoSignCert).isFile())
                        throw new Exception("agent.tls-sni-erasure.cert-key.auto-sign cert is not a file");
                    if (!new File(autoSignKey).isFile())
                        throw new Exception("agent.tls-sni-erasure.cert-key.auto-sign key is not a file");
                    if (args.size() == 3) {
                        autoSignWorkingDirectory = new File(Utils.filename(args.get(2)));
                        if (!autoSignWorkingDirectory.isDirectory()) {
                            throw new Exception("agent.https-sni-erasure.cert-key.auto-sign tempDir is not a directory");
                        }
                    } else {
                        // allocate the temporary directory for auto signing
                        autoSignWorkingDirectory = Files.createTempDirectory("vpws-agent-auto-sign").toFile();
                        autoSignWorkingDirectory.deleteOnExit();
                    }
                } // indent for git diff
            } // indent for git diff
        } // indent for git diff
        for (var group : config.getProxy().getGroups()) {
            String currentAlias = group.getName();
            for (var line : group.getServers()) {
                if (!line.startsWith("websocks://") && !line.startsWith("websockss://")
                    && !line.startsWith("websocks:kcp://") && !line.startsWith("websockss:kcp://")
                    && !line.startsWith("websocks:uot:kcp://") && !line.startsWith("websockss:uot:kcp://")) {
                    throw new Exception("unknown protocol: " + line);
                }

                boolean useSSL = line.startsWith("websockss");
                boolean useKCP = line.contains(":kcp://");
                boolean useUOT = line.contains(":uot:");
                // format line
                if (useSSL) {
                    if (useKCP) {
                        if (useUOT) {
                            line = line.substring("websockss:uot:kcp://".length());
                        } else {
                            line = line.substring("websockss:kcp://".length());
                        }
                    } else {
                        line = line.substring("websockss://".length());
                    }
                } else {
                    if (useKCP) {
                        if (useUOT) {
                            line = line.substring("websocks:uot:kcp://".length());
                        } else {
                            line = line.substring("websocks:kcp://".length());
                        }
                    } else {
                        line = line.substring("websocks://".length());
                    }
                }

                int colonIdx = line.lastIndexOf(':');
                if (colonIdx == -1)
                    throw new Exception("invalid address:port for proxy.server.list: " + line);
                String hostPart = line.substring(0, colonIdx);
                String portPart = line.substring(colonIdx + 1);
                if (hostPart.isEmpty())
                    throw new Exception("invalid host: " + line);
                int port;
                try {
                    port = Integer.parseInt(portPart);
                } catch (NumberFormatException e) {
                    throw new Exception("invalid port: " + line);
                }
                if (port < 1 || port > 65535) {
                    throw new Exception("invalid port: " + line);
                }

                getGroup(currentAlias).add(useSSL, useKCP, useUOT, hostPart, port);
            }
            for (var line : group.getDomains()) {
                getDomainList(currentAlias).add(formatDomainChecker(line));
            }
            for (var line : group.getResolve()) {
                getProxyResolveList(currentAlias).add(formatDomainChecker(line));
            }
            for (var line : group.getNoProxy()) {
                getNoProxyDomainList(currentAlias).add(formatDomainChecker(line));
            }
        }
        { // indent for git diff
            for (var line : config.getAgent().getTlsSniErasure().getDomains()) {
                httpsSniErasureDomains.add(formatDomainChecker(line));
            }
            for (var ls : config.getAgent().getTlsSniErasure().getCertKeyList()) {
                if (ls.isEmpty())
                    continue;
                httpsSniErasureCertKeyFiles.add(ls);
            }
        } // indent for git diff
    }

    public List<String> validate() {
        List<String> failReasons = new LinkedList<>();

        // check for variables must present
        if (user == null || pass == null)
            failReasons.add("proxy.server.auth not present");
        // check for https relay
        if (autoSignCert == null) {
            if (directRelayIpRange == null) {
                if (!httpsSniErasureDomains.isEmpty()) {
                    failReasons.add("agent.https-sni-erasure.cert-key.list is empty and auto-sign is disabled, but https-sni-erasure.domain.list is not empty");
                }
                if (directRelay) {
                    failReasons.add("agent.https-sni-erasure.cert-key.list is empty and auto-sign is disabled, but agent.direct-relay is enabled");
                }
            }
        }
        // check for direct relay switch
        if (!directRelay) {
            if (directRelayIpRange != null) {
                failReasons.add("agent.direct-relay is disabled, but agent.direct-relay.ip-range is set");
            }
            if (directRelayListen != null) {
                failReasons.add("agent.direct-relay is disabled, but agent.direct-relay.listen is set");
            }
        }
        // check for direct-relay.ip-range/listen
        if (directRelayIpRange != null && directRelayListen == null) {
            failReasons.add("agent.direct-relay.ip-range is set, but agent.direct-relay.listen is not set");
        }
        if (directRelayIpRange == null && directRelayListen != null) {
            failReasons.add("agent.direct-relay.ip-range is not set, but agent.direct-relay.listen is set");
        }
        // check for https-sni-erasure and certificates configuration
        if (!httpsSniErasureDomains.isEmpty()) {
            if (autoSignCert == null && httpsSniErasureCertKeyFiles.isEmpty()) {
                failReasons.add("https-sni-erasure.domain.list is set, but neither agent.https-sni-erasure.cert-key.auto-sign nor agent.https-sni-erasure.cert-key.list set");
            }
        }
        // check for consistency of server list and domain list
        for (String k : domains.keySet()) {
            if (!servers.containsKey(k))
                failReasons.add(k + " is defined in domain list, but not in server list");
        }
        // check for consistency of server list and resolve list
        for (String k : proxyResolves.keySet()) {
            if (!servers.containsKey(k))
                failReasons.add(k + " is defined in resolve list, but not in server list");
        }
        // check for consistency of server list and no-proxy list
        for (String k : noProxyDomains.keySet()) {
            if (!servers.containsKey(k))
                failReasons.add(k + " is defined in resolve list, but not in server list");
        }
        // check for pac server
        if (pacServerPort != 0) {
            if (socks5ListenPort == 0 && httpConnectListenPort == 0) {
                failReasons.add("pac server is defined, but neither socks5-server nor http-connect-server is defined");
            }
        }
        // check for ss
        if (ssListenPort != 0 && ssPassword.isEmpty()) {
            failReasons.add("ss is enabled by agent.ss.listen, but agent.ss.password is not set");
        }
        // check udp over tcp (uot)
        if (udpOverTcpEnabled) {
            if (udpOverTcpNic == null) {
                udpOverTcpNic = "eth0";
            }
        } else {
            if (udpOverTcpNic != null) {
                failReasons.add("proxy.server.uot is disabled but proxy.server.uot.nic is set: " + udpOverTcpNic);
            }
        }
        // uot servers should not exist if uot not enabled
        if (!udpOverTcpEnabled) {
            out:
            for (var entry : servers.entrySet()) {
                var group = entry.getKey();
                var ls = entry.getValue().getServers();
                for (var svr : ls) {
                    if (svr.useUOT()) {
                        failReasons.add("proxy.server.uot is not enabled, but server group " + group + " has server using uot");
                        continue out;
                    }
                }
            }
        }

        return failReasons;
    }

    private DomainChecker formatDomainChecker(String line) throws Exception {
        if (line.startsWith(":")) {
            String portStr = line.substring(1);
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new Exception("invalid port rule: " + portStr);
            }
            return new DomainChecker.PortChecker(port);
        } else if (line.startsWith("/") && line.endsWith("/")) {
            String regexp = line.substring(1, line.length() - 1);
            return new DomainChecker.PatternDomainChecker(Pattern.compile(regexp));
        } else if (line.startsWith("[") && line.endsWith("]")) {
            final String abpfile = line.substring(1, line.length() - 1).trim();
            String content;
            if (abpfile.contains("://")) {
                Logger.alert("getting abp from " + abpfile);
                Promise<ByteArray> contentPromise = CoroutineHttp1ClientConnection.simpleGet(abpfile, true);
                ByteArray contentBytes;
                try {
                    contentBytes = contentPromise.block();
                } catch (Exception e) {
                    throw e;
                } catch (Throwable t) {
                    throw new Exception(t);
                }
                content = new String(contentBytes.toJavaArray());
                content = Arrays.stream(content.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining());
            } else {
                String filename = Utils.filename(abpfile);
                try (FileReader fileABP = new FileReader(filename)) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader br2 = new BufferedReader(fileABP);
                    String line2;
                    while ((line2 = br2.readLine()) != null) {
                        sb.append(line2.trim());
                    }
                    content = sb.toString();
                }
            }

            ABP abp = new ABP(abpfile, false);
            abp.addBase64(content);
            return new DomainChecker.ABPDomainChecker(abp);
        } else {
            return new DomainChecker.SuffixDomainChecker(line);
        }
    }

    public JSON.Object toJson() {
        var builder = new ObjectBuilder();
        if (socks5ListenPort != 0) {
            builder.putObject("socks5", o -> o
                .put("enabled", true)
                .put("listen", socks5ListenPort));
        }
        if (httpConnectListenPort != 0) {
            builder.putObject("httpconnect", o -> o
                .put("enabled", true)
                .put("listen", httpConnectListenPort));
        }
        if (ssListenPort != 0) {
            builder.putObject("ss", o -> o
                .put("enabled", true)
                .put("listen", ssListenPort)
                .put("password", ssPassword));
        }
        if (dnsListenPort != 0) {
            builder.putObject("dns", o -> o
                .put("enabled", true)
                .put("listen", dnsListenPort));
        }
        if (pacServerPort != 0) {
            builder.putObject("pac", o -> o
                .put("enabled", true)
                .put("listen", pacServerPort));
        }
        if (gateway) {
            builder.putObject("gateway", o -> o
                .put("enabled", true));
        }
        if (autoSignCert != null) {
            builder.putObject("autosign", o -> o
                .put("enabled", true)
                .put("cacert", autoSignCert)
                .put("cakey", autoSignKey));
        }
        if (directRelay) {
            builder.putObject("directrelay", o -> o
                .put("enabled", true));
        }
        if (directRelayIpRange != null) {
            builder.putObject("directrelayadvanced", o -> o
                .put("enabled", true)
                .put("network", directRelayIpRange.toString())
                .put("listen", directRelayListen.formatToIPPortString())
                .put("timeout", directRelayIpBondTimeout));
        }
        if (udpOverTcpEnabled) {
            builder.putObject("uot", o -> o
                .put("enabled", true)
                .put("nic", udpOverTcpNic));
        }
        if (user != null) {
            builder.put("serverUser", user);
        }
        if (pass != null) {
            builder.put("serverPass", pass);
        }
        builder.putObject("hc", o -> o.put("enabled", !noHealthCheck));
        builder.putObject("certauth", o -> o.put("enabled", verifyCert));
        builder.putArray("serverGroupList", a -> {
            for (var entry : servers.entrySet()) {
                var groupName = entry.getKey();
                var group = entry.getValue();
                var domainList = getDomainList(groupName);
                var noProxyList = getNoProxyDomainList(groupName);
                var resolveList = getProxyResolveList(groupName);
                a.addObject(sg -> sg
                    .put("name", groupName)
                    .putArray("serverList", ls -> {
                        for (var server : group.getServers()) {
                            ls.addObject(svr -> {
                                if (server.useSSL()) {
                                    svr.put("protocol", "websockss");
                                } else {
                                    svr.put("protocol", "websocks");
                                }
                                svr.putObject("kcp", o -> o
                                    .put("enabled", server.useKCP())
                                    .putObject("uot", o2 -> o2.put("enabled", server.useUOT()))
                                );
                                svr.put("ip", server.host);
                                svr.put("port", server.port);
                            });
                        }
                    })
                    .putArray("proxyRuleList", rules -> {
                        for (var domain : domainList) {
                            rules.addObject(rule -> rule
                                .put("rule", domain.serialize())
                                .putObject("white", w -> w.put("enabled", false))
                            );
                        }
                        for (var domain : noProxyList) {
                            rules.addObject(rule -> rule
                                .put("rule", domain.serialize())
                                .putObject("white", w -> w.put("enabled", true)));
                        }
                    })
                    .putArray("dnsRuleList", rules -> {
                        for (var rule : resolveList) {
                            rules.addObject(ruleO -> ruleO
                                .put("rule", rule.serialize()));
                        }
                    }));
            }
        });
        builder.putArray("httpsSniErasureRuleList", a -> {
            for (var domain : httpsSniErasureDomains) {
                a.addObject(o -> o
                    .put("rule", domain.serialize()));
            }
        });
        return builder.build();
    }
}
