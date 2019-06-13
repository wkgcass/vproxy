package net.cassite.vproxyx.websocks;

import net.cassite.legacy.JsContext;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;
import net.cassite.vproxy.util.Utils;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ConfigProcessor {
    public final String fileName;
    public final EventLoopGroup hcLoopGroup;
    private int listenPort = 1080;
    private int httpConnectListenPort = 0;
    private boolean gateway = false;
    private Map<String, ServerGroup> servers = new HashMap<>();
    private Map<String, List<DomainChecker>> domains = new HashMap<>();
    private String user;
    private String pass;
    private String cacertsPath;
    private String cacertsPswd;
    private boolean strictMode = false;
    private int poolSize = 10;

    private String pacServerIp;
    private int pacServerPort;

    public ConfigProcessor(String fileName, EventLoopGroup hcLoopGroup) {
        this.fileName = fileName;
        this.hcLoopGroup = hcLoopGroup;
    }

    public int getListenPort() {
        return listenPort;
    }

    public int getHttpConnectListenPort() {
        return httpConnectListenPort;
    }

    public boolean isGateway() {
        return gateway;
    }

    public Map<String, ServerGroup> getServers() {
        return servers;
    }

    public Map<String, List<DomainChecker>> getDomains() {
        return domains;
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

    public boolean isStrictMode() {
        return strictMode;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public String getPacServerIp() {
        return pacServerIp;
    }

    public int getPacServerPort() {
        return pacServerPort;
    }

    private ServerGroup getGroup(String alias) throws Exception {
        if (alias == null) {
            alias = "DEFAULT";
        }
        if (servers.containsKey(alias))
            return servers.get(alias);
        ServerGroup grp = new ServerGroup(alias, hcLoopGroup,
            new HealthCheckConfig(5_000, 30_000, 1, 2)
            , Method.wrr);
        servers.put(alias, grp);
        return grp;
    }

    private List<DomainChecker> getDomainList(String alias) {
        if (alias == null) {
            alias = "DEFAULT";
        }
        if (domains.containsKey(alias))
            return domains.get(alias);
        List<DomainChecker> checkers = new LinkedList<>();
        domains.put(alias, checkers);
        return checkers;
    }

    public void parse() throws Exception {
        FileInputStream inputStream = new FileInputStream(fileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        int step = 0;
        String currentAlias = null;
        // 0 -> normal
        // 1 -> proxy.server.list
        // 2 -> proxy.domain.list
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue; // ignore whitespace lines and comment lines

            if (step == 0) {
                if (line.startsWith("agent.listen ")) {
                    String port = line.substring("agent.listen ".length()).trim();
                    try {
                        listenPort = Integer.parseInt(port);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.listen, expecting an integer");
                    }
                    if (listenPort < 1 || listenPort > 65535) {
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
                } else if (line.startsWith("agent.gateway ")) {
                    String val = line.substring("agent.gateway ".length());
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
                } else if (line.startsWith("agent.cacerts.path ")) {
                    String path = line.substring("agent.cacerts.path ".length()).trim();
                    if (path.isEmpty())
                        throw new Exception("cacert path not specified");
                    cacertsPath = path;
                } else if (line.startsWith("agent.cacerts.pswd ")) {
                    String pswd = line.substring("agent.cacerts.pswd ".length()).trim();
                    if (pswd.isEmpty())
                        throw new Exception("cacert path not specified");
                    cacertsPswd = pswd;
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
                } else if (line.startsWith("agent.gateway.pac.address ")) {
                    String val = line.substring("agent.gateway.pac.address ".length()).trim();
                    String[] split = val.split(":");
                    if (split.length != 2)
                        throw new Exception("invalid agent.gateway.pac.address, should be $ip:$port");
                    String ip = split[0];
                    if (!ip.equals("*") && !Utils.isIpLiteral(ip))
                        throw new Exception("invalid agent.gateway.pac.address, the ip is invalid");
                    String portStr = split[1];
                    int port;
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid agent.gateway.pac.address, the port is invalid");
                    }
                    pacServerIp = ip;
                    pacServerPort = port;
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
                } else {
                    throw new Exception("unknown line: " + line);
                }
            } else if (step == 1) {
                if (line.equals("proxy.server.list.end")) {
                    step = 0; // return to normal state
                    currentAlias = null;
                    continue;
                }
                if (!line.startsWith("websocks://") && !line.startsWith("websockss://")) {
                    throw new Exception("unknown protocol: " + line);
                }

                boolean useSSL = line.startsWith("websockss");
                line = line.substring(useSSL ? "websockss://".length() : "websocks://".length());

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
                ServerGroup.ServerHandle handle;
                if (Utils.parseIpv4StringConsiderV6Compatible(hostPart) != null) {
                    InetAddress inet = InetAddress.getByName(hostPart);
                    handle = getGroup(currentAlias).add(hostPart, new InetSocketAddress(inet, port), 10);
                } else if (Utils.isIpv6(hostPart)) {
                    InetAddress inet = InetAddress.getByName(hostPart);
                    handle = getGroup(currentAlias).add(hostPart, new InetSocketAddress(inet, port), 10);
                } else {
                    BlockCallback<InetAddress, IOException> cb = new BlockCallback<>();
                    Resolver.getDefault().resolveV4(hostPart, cb);
                    InetAddress inet = cb.block();
                    handle = getGroup(currentAlias).add(hostPart, hostPart, new InetSocketAddress(inet, port), 10);
                }

                // this will be used when connection establishes to remote
                // in WebSocksProxyAgentConnectorProvider.java
                handle.data = useSSL;
            } else {
                //noinspection ConstantConditions
                assert step == 2;
                if (line.equals("proxy.domain.list.end")) {
                    step = 0;
                    currentAlias = null;
                    continue;
                }
                if (line.startsWith(":")) {
                    String portStr = line.substring(1);
                    int port;
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw new Exception("invalid port rule: " + portStr);
                    }
                    getDomainList(currentAlias).add(new PortChecker(port));
                } else if (line.startsWith("/") && line.endsWith("/")) {
                    String regexp = line.substring(1, line.length() - 1);
                    getDomainList(currentAlias).add(new PatternDomainChecker(Pattern.compile(regexp)));
                } else if (line.startsWith("[") && line.endsWith("]")) {
                    String pacfile = line.substring(1, line.length() - 1);
                    if (pacfile.startsWith("~")) {
                        pacfile = System.getProperty("user.home") + File.separator + pacfile.substring(1);
                    }

                    JsContext jsContext = JsContext.newContext();
                    String pacScript;
                    try (FileReader filePac = new FileReader(pacfile)) {
                        StringBuilder sb = new StringBuilder();
                        BufferedReader br2 = new BufferedReader(filePac);
                        String line2;
                        while ((line2 = br2.readLine()) != null) {
                            sb.append(line2).append("\n");
                        }
                        pacScript = sb.toString();
                    }
                    jsContext.eval(pacScript, Object.class);
                    getDomainList(currentAlias).add(new PacDomainChecker(jsContext));
                } else {
                    getDomainList(currentAlias).add(new SuffixDomainChecker(line));
                }
            }
        }

        // check for variables must present
        if (user == null || pass == null)
            throw new Exception("proxy.server.auth not present");
        // check for consistency of server list and domain list
        for (String k : servers.keySet()) {
            if (!domains.containsKey(k))
                throw new Exception(k + " is defined in server list, but not in domain list");
        }
        for (String k : domains.keySet()) {
            if (!servers.containsKey(k))
                throw new Exception(k + " is defined in domain list, but not in server list");
        }
        // check for listening ports
        if (listenPort == httpConnectListenPort) {
            throw new Exception("agent.listen and agent.httpconnect.listen are the same");
        }
    }
}
