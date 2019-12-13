package vproxyx.websocks;

import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.connection.NetEventLoop;
import vproxy.dns.Resolver;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.h2streamed.H2StreamedClientFDs;
import vproxy.selector.wrap.kcp.KCPFDs;
import vproxy.util.BlockCallback;
import vproxy.util.Utils;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.regex.Pattern;

public class ConfigProcessor {
    public final String fileName;
    public final EventLoopGroup hcLoopGroup;
    public final EventLoopGroup workerLoopGroup;
    private int listenPort = 1080;
    private int httpConnectListenPort = 0;
    private int ssListenPort = 0;
    private String ssPassword = "";
    private boolean gateway = false;
    private Map<String, ServerGroup> servers = new HashMap<>();
    private Map<String, List<DomainChecker>> domains = new HashMap<>();
    private String user;
    private String pass;
    private String cacertsPath;
    private String cacertsPswd;
    private boolean verifyCert = true;
    private boolean strictMode = false;
    private int poolSize = 10;
    private boolean noHealthCheck = false;

    private String pacServerIp;
    private int pacServerPort;

    public ConfigProcessor(String fileName, EventLoopGroup hcLoopGroup, EventLoopGroup workerLoopGroup) {
        this.fileName = fileName;
        this.hcLoopGroup = hcLoopGroup;
        this.workerLoopGroup = workerLoopGroup;
    }

    public int getListenPort() {
        return listenPort;
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

    public boolean isGateway() {
        return gateway;
    }

    public Map<String, ServerGroup> getServers() {
        return servers;
    }

    public LinkedHashMap<String, List<DomainChecker>> getDomains() {
        LinkedHashMap<String, List<DomainChecker>> ret = new LinkedHashMap<>();
        for (String key : domains.keySet()) {
            if (!key.equals("DEFAULT")) {
                ret.put(key, domains.get(key));
            }
        }
        // put DEFAULT to the last
        if (domains.containsKey("DEFAULT")) {
            ret.put("DEFAULT", domains.get("DEFAULT"));
        }
        return ret;
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
                    cacertsPath = path;
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

                String program = null;
                int programPort = 0;
                {
                    String[] split = line.split(" ");
                    if (split.length > 1) {
                        line = split[0];
                        StringBuilder sb = new StringBuilder(split[1]);
                        for (int i = 2; i < split.length; ++i) {
                            sb.append(" ").append(split[i]);
                        }
                        program = sb.toString();
                        program = program.replace("~", System.getProperty("user.home"));
                        programPort = (int) (30000 + 10000 * Math.random());
                        program = program.replace("$LOCAL_PORT", "" + programPort);
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

                if (program != null) {
                    program = program.replace("$SERVER_IP", hostPart);
                    program = program.replace("$SERVER_PORT", portPart);

                    final var finalProgram = program;

                    System.out.println("running program: [" + program + "]");
                    Process p = Utils.runSubProcess(program);
                    p.onExit().thenAccept(pp -> System.err.println("sub process [" + finalProgram + "] exits with " + pp.exitValue()));
                    Utils.proxyProcessOutput(p);
                }

                ServerGroup.ServerHandle handle;
                if (program != null) {
                    InetAddress inet;
                    if (Utils.isIpLiteral(hostPart)) {
                        inet = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
                    } else {
                        inet = InetAddress.getByAddress(hostPart, new byte[]{127, 0, 0, 1});
                    }
                    handle = getGroup(currentAlias).add(line, new InetSocketAddress(inet, programPort), 10);
                } else if (Utils.parseIpv4StringConsiderV6Compatible(hostPart) != null) {
                    InetAddress inet = InetAddress.getByName(hostPart);
                    handle = getGroup(currentAlias).add(line, new InetSocketAddress(inet, port), 10);
                } else if (Utils.isIpv6(hostPart)) {
                    InetAddress inet = InetAddress.getByName(hostPart);
                    handle = getGroup(currentAlias).add(line, new InetSocketAddress(inet, port), 10);
                } else {
                    BlockCallback<InetAddress, IOException> cb = new BlockCallback<>();
                    Resolver.getDefault().resolveV4(hostPart, cb);
                    InetAddress inet = cb.block();
                    handle = getGroup(currentAlias).add(line, hostPart, new InetSocketAddress(inet, port), 10);
                }

                // init streamed fds
                Map<SelectorEventLoop, H2StreamedClientFDs> fds = new HashMap<>();
                if (useKCP) {
                    {
                        // build fds map
                        Set<NetEventLoop> set = new HashSet<>();
                        while (true) {
                            NetEventLoop l = workerLoopGroup.next();
                            if (!set.add(l)) {
                                // all loops visited
                                break;
                            }
                            // build for this remote server
                            KCPFDs kcpFDs = KCPFDs.getClientDefault();
                            H2StreamedClientFDs h2sFDs = new H2StreamedClientFDs(kcpFDs, l.getSelectorEventLoop(),
                                handle.server);
                            fds.put(l.getSelectorEventLoop(), h2sFDs);
                        }
                    }
                }
                // this will be used when connection establishes to remote
                // in WebSocksProxyAgentConnectorProvider.java
                handle.data = new SharedData(useSSL, useKCP, fds);
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
                    String abpfile = line.substring(1, line.length() - 1);
                    if (abpfile.startsWith("~")) {
                        abpfile = System.getProperty("user.home") + File.separator + abpfile.substring(1);
                    }

                    ABP abp;
                    try (FileReader fileABP = new FileReader(abpfile)) {
                        StringBuilder sb = new StringBuilder();
                        BufferedReader br2 = new BufferedReader(fileABP);
                        String line2;
                        while ((line2 = br2.readLine()) != null) {
                            sb.append(line2.trim());
                        }
                        var content = sb.toString();
                        abp = new ABP(true);
                        abp.addBase64(content);
                    }
                    getDomainList(currentAlias).add(new ABPDomainChecker(abp));
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
        if (listenPort == ssListenPort) {
            throw new Exception("agent.listen and agent.ss.listen are the same");
        }
        // check for ss
        if (ssListenPort != 0 && ssPassword.isEmpty()) {
            throw new Exception("ss is enabled by agent.ss.listen, but agent.ss.password is not set");
        }
        // modify server group if noHealthCheck
        if (noHealthCheck) {
            for (ServerGroup g : servers.values()) {
                g.setHealthCheckConfig(new HealthCheckConfig(
                    Integer.MAX_VALUE, Integer.MAX_VALUE, 1, Integer.MAX_VALUE
                ));
                for (ServerGroup.ServerHandle svr : g.getServerHandles()) {
                    svr.healthy = true;
                }
            }
        }
    }
}
