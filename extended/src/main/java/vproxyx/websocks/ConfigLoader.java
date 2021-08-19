package vproxyx.websocks;

import vjson.JSON;
import vjson.util.ObjectBuilder;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.base.util.Network;
import vproxy.base.util.Utils;
import vproxy.base.util.promise.Promise;
import vproxy.lib.http1.CoroutineHttp1ClientConnection;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

import java.io.*;
import java.nio.file.Files;
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
    private int directRelayIpBondTimeout = 10_000;
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

    public void load(String fileName) throws Exception {
        FileInputStream inputStream = new FileInputStream(fileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        int step = 0;
        String currentAlias = null;
        // 0 -> normal
        // 1 -> proxy.server.list
        // 2 -> proxy.domain.list
        // 3 -> proxy.resolve.list
        // 4 -> no-proxy.domain.list
        // 5 -> https-sni-erasure.domain.list
        // 6 -> agent.https-sni-erasure.cert-key.list
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue; // ignore whitespace lines and comment lines

            if (step == 0) {
                if (line.startsWith("agent.listen ") || line.startsWith("agent.socks5.listen ")) {
                    int prefixLen = (line.startsWith("agent.listen ")) ? "agent.listen ".length() : "agent.socks5.listen ".length();
                    String port = line.substring(prefixLen).trim();
                    try {
                        socks5ListenPort = Integer.parseInt(port);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.listen, expecting an integer");
                    }
                    if (socks5ListenPort < 1 || socks5ListenPort > 65535) {
                        throw new Exception("invalid agent.listen, port number out of range");
                    }
                } else if (line.startsWith("agent.httpconnect.listen ")) {
                    String port = line.substring("agent.httpconnect.listen ".length()).trim();
                    try {
                        httpConnectListenPort = Integer.parseInt(port);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.httpconnect.listen, expecting an integer");
                    }
                    if (httpConnectListenPort < 1 || httpConnectListenPort > 65535) {
                        throw new Exception("invalid agent.httpconnect.listen, port number out of range");
                    }
                } else if (line.startsWith("agent.ss.listen ")) {
                    String port = line.substring("agent.ss.listen ".length()).trim();
                    try {
                        ssListenPort = Integer.parseInt(port);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.ss.listen, expecting an integer");
                    }
                    if (ssListenPort < 1 || ssListenPort > 65535) {
                        throw new Exception("invalid agent.ss.listen, port number out of range");
                    }
                } else if (line.startsWith("agent.ss.password ")) {
                    ssPassword = line.substring("agent.ss.password ".length()).trim();
                } else if (line.startsWith("agent.dns.listen ")) {
                    String port = line.substring("agent.dns.listen ".length()).trim();
                    try {
                        dnsListenPort = Integer.parseInt(port);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.dns.listen, expecting an integer");
                    }
                    if (dnsListenPort < 1 || dnsListenPort > 65535) {
                        throw new Exception("invalid agent.dns.listen, port number out of range");
                    }
                } else if (line.startsWith("agent.gateway ")) {
                    String val = line.substring("agent.gateway ".length()).trim();
                    switch (val) {
                        case "on":
                            gateway = true;
                            break;
                        case "off":
                            gateway = false;
                            break;
                        default:
                            throw new Exception("invalid value for agent.gateway: " + val);
                    }
                } else if (line.startsWith("agent.direct-relay ")) {
                    String val = line.substring("agent.direct-relay ".length()).trim();
                    switch (val) {
                        case "on":
                            directRelay = true;
                            break;
                        case "off":
                            directRelay = false;
                            break;
                        default:
                            throw new Exception("invalid value for agent.direct-relay: " + val);
                    }
                } else if (line.startsWith("agent.direct-relay.ip-range ")) {
                    String val = line.substring("agent.direct-relay.ip-range ".length()).trim();
                    if (!Network.validNetworkStr(val)) {
                        throw new Exception("invalid network in agent.direct-relay.ip-range: " + val);
                    }
                    directRelayIpRange = new Network(val);
                } else if (line.startsWith("agent.direct-relay.listen ")) {
                    String val = line.substring("agent.direct-relay.listen ".length()).trim();
                    if (!IPPort.validL4AddrStr(val)) {
                        throw new Exception("invalid binding address in agent.direct-relay.listen: " + val);
                    }
                    String host = val.substring(0, val.lastIndexOf(":"));
                    int port = Integer.parseInt(val.substring(val.lastIndexOf(":") + 1));
                    directRelayListen = new IPPort(IP.from(host), port);
                } else if (line.startsWith("agent.direct-relay.ip-bond-timeout ")) {
                    String val = line.substring("agent.direct-relay.ip-bond-timeout ".length()).trim();
                    int timeout;
                    try {
                        timeout = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid value for agent.direct-relay.ip-bond-timeout, should be a number: " + val);
                    }
                    directRelayIpBondTimeout = timeout * 1000;
                } else if (line.startsWith("proxy.server.auth ")) {
                    String auth = line.substring("proxy.server.auth ".length()).trim();
                    String[] userpass = auth.split(":");
                    if (userpass.length != 2)
                        throw new Exception("invalid proxy.server.auth: " + auth);
                    user = userpass[0].trim();
                    if (user.isEmpty())
                        throw new Exception("invalid proxy.server.auth: user is empty");
                    pass = userpass[1].trim();
                    if (pass.isEmpty())
                        throw new Exception("invalid proxy.server.auth: pass is empty");
                } else if (line.startsWith("proxy.server.hc ")) {
                    String hc = line.substring("proxy.server.hc ".length());
                    if (hc.equals("on")) {
                        noHealthCheck = false;
                    } else if (hc.equals("off")) {
                        noHealthCheck = true;
                    } else {
                        throw new Exception("invalid value for proxy.server.hc: " + hc);
                    }
                } else if (line.startsWith("agent.cacerts.path ")) {
                    String path = line.substring("agent.cacerts.path ".length()).trim();
                    if (path.isEmpty())
                        throw new Exception("cacert path not specified");
                    cacertsPath = Utils.filename(path);
                } else if (line.startsWith("agent.cacerts.pswd ")) {
                    String pswd = line.substring("agent.cacerts.pswd ".length()).trim();
                    if (pswd.isEmpty())
                        throw new Exception("cacert path not specified");
                    cacertsPswd = pswd;
                } else if (line.startsWith("agent.cert.verify ")) {
                    String val = line.substring("agent.cert.verify ".length()).trim();
                    switch (val) {
                        case "on":
                            verifyCert = true;
                            break;
                        case "off":
                            verifyCert = false;
                            break;
                        default:
                            throw new Exception("invalid value for agent.cert.verify: " + val);
                    }
                } else if (line.startsWith("agent.strict ")) {
                    String val = line.substring("agent.strict ".length()).trim();
                    switch (val) {
                        case "on":
                            strictMode = true;
                            break;
                        case "off":
                            strictMode = false;
                            break;
                        default:
                            throw new Exception("invalid value for agent.strict: " + val);
                    }
                } else if (line.startsWith("agent.pool ")) {
                    String size = line.substring("agent.pool ".length()).trim();
                    int intSize;
                    try {
                        intSize = Integer.parseInt(size);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.pool, expecting an integer");
                    }
                    if (intSize < 0) {
                        throw new Exception("invalid agent.pool, should not be negative");
                    }
                    poolSize = intSize;
                } else if (line.startsWith("agent.gateway.pac.listen ")) {
                    String val = line.substring("agent.gateway.pac.listen ".length()).trim();
                    int port;
                    try {
                        port = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.gateway.pac.listen, the port is invalid");
                    }
                    pacServerPort = port;
                } else if (line.startsWith("proxy.server.udp-over-tcp ")) {
                    String val = line.substring("proxy.server.udp-over-tcp ".length()).trim();
                    if (val.equals("on")) {
                        udpOverTcpEnabled = true;
                    } else if (val.equals("off")) {
                        udpOverTcpEnabled = false;
                    } else {
                        throw new Exception("invalid value for proxy.server.udp-over-tcp: " + val);
                    }
                } else if (line.startsWith("proxy.server.udp-over-tcp.nic ")) {
                    udpOverTcpNic = line.substring("proxy.server.udp-over-tcp.nic ".length()).trim();
                } else if (line.startsWith("agent.https-sni-erasure.cert-key.auto-sign ")) {
                    line = line.substring("agent.https-sni-erasure.cert-key.auto-sign ".length());
                    var args = Arrays.stream(line.split(" ")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    if (args.isEmpty()) {
                        continue;
                    }
                    if (args.size() != 2 && args.size() != 3) {
                        throw new Exception("agent.https-sni-erasure.cert-key.auto-sign should take exactly two arguments");
                    }
                    autoSignCert = Utils.filename(args.get(0));
                    autoSignKey = Utils.filename(args.get(1));
                    if (!new File(autoSignCert).isFile())
                        throw new Exception("agent.https-sni-erasure.cert-key.auto-sign cert is not a file");
                    if (!new File(autoSignKey).isFile())
                        throw new Exception("agent.https-sni-erasure.cert-key.auto-sign key is not a file");
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
                } else if (line.startsWith("proxy.server.list.start")) {
                    step = 1; // retrieving server list
                    if (!line.equals("proxy.server.list.start")) {
                        String alias = line.substring("proxy.server.list.start".length()).trim();
                        if (alias.split(" ").length > 1)
                            throw new Exception("symbol cannot contain spaces");
                        currentAlias = alias;
                    }
                } else if (line.startsWith("proxy.domain.list.start")) {
                    step = 2;
                    if (!line.equals("proxy.domain.list.start")) {
                        String alias = line.substring("proxy.domain.list.start".length()).trim();
                        if (alias.split(" ").length > 1)
                            throw new Exception("symbol cannot contain spaces");
                        currentAlias = alias;
                    }
                } else if (line.startsWith("proxy.resolve.list.start")) {
                    step = 3;
                    if (!line.equals("proxy.resolve.list.start")) {
                        String alias = line.substring("proxy.resolve.list.start".length()).trim();
                        if (alias.split(" ").length > 1)
                            throw new Exception("symbol cannot contain spaces");
                        currentAlias = alias;
                    }
                } else if (line.startsWith("no-proxy.domain.list.start")) {
                    step = 4;
                    if (!line.equals("no-proxy.domain.list.start")) {
                        String alias = line.substring("no-proxy.domain.list.start".length()).trim();
                        if (alias.split(" ").length > 1)
                            throw new Exception("symbol cannot contain spaces");
                        currentAlias = alias;
                    }
                } else if (line.equals("https-sni-erasure.domain.list.start")) {
                    step = 5;
                } else if (line.equals("agent.https-sni-erasure.cert-key.list.start")) {
                    step = 6;
                } else {
                    throw new Exception("unknown line: " + line);
                }
            } else if (step == 1) {
                if (line.equals("proxy.server.list.end")) {
                    step = 0; // return to normal state
                    currentAlias = null;
                    continue;
                }
                if (!line.startsWith("websocks://") && !line.startsWith("websockss://")
                    && !line.startsWith("websocks:kcp://") && !line.startsWith("websockss:kcp://")) {
                    throw new Exception("unknown protocol: " + line);
                }

                boolean useSSL = line.startsWith("websockss");
                boolean useKCP = line.contains(":kcp://");
                // format line
                if (useSSL) {
                    if (useKCP) {
                        line = line.substring("websockss:kcp://".length());
                    } else {
                        line = line.substring("websockss://".length());
                    }
                } else {
                    if (useKCP) {
                        line = line.substring("websocks:kcp://".length());
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

                getGroup(currentAlias).add(useSSL, useKCP, hostPart, port);
            } else if (step == 2) {
                if (line.equals("proxy.domain.list.end")) {
                    step = 0;
                    currentAlias = null;
                    continue;
                }
                getDomainList(currentAlias).add(formatDomainChecker(line));
            } else if (step == 3) {
                if (line.equals("proxy.resolve.list.end")) {
                    step = 0;
                    currentAlias = null;
                    continue;
                }
                getProxyResolveList(currentAlias).add(formatDomainChecker(line));
            } else if (step == 4) {
                if (line.equals("no-proxy.domain.list.end")) {
                    step = 0;
                    currentAlias = null;
                    continue;
                }
                getNoProxyDomainList(currentAlias).add(formatDomainChecker(line));
            } else if (step == 5) {
                if (line.equals("https-sni-erasure.domain.list.end")) {
                    step = 0;
                    continue;
                }
                httpsSniErasureDomains.add(formatDomainChecker(line));
            } else {
                //noinspection ConstantConditions
                assert step == 6;
                if (line.equals("agent.https-sni-erasure.cert-key.list.end")) {
                    step = 0;
                    continue;
                }
                var ls = Arrays.stream(line.split(" ")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                if (ls.isEmpty())
                    continue;
                httpsSniErasureCertKeyFiles.add(ls);
            }
        }
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
        // check udp over tcp
        if (udpOverTcpEnabled) {
            if (udpOverTcpNic == null) {
                udpOverTcpNic = "eth0";
            }
        } else {
            if (udpOverTcpNic != null) {
                failReasons.add("proxy.server.udp-over-tcp is disabled but proxy.server.udp-over-tcp.nic is set: " + udpOverTcpNic);
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
                Promise<ByteArray> contentPromise = CoroutineHttp1ClientConnection.simpleGet(abpfile);
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
            builder.putObject("udpovertcp", o -> o
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
                                if (server.useSSL) {
                                    svr.put("protocol", "websockss");
                                } else {
                                    svr.put("protocol", "websocks");
                                }
                                svr.putObject("kcp", o -> o.put("enabled", server.useKCP));
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
