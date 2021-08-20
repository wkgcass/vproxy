package vproxyx.websocks;

import vproxy.base.component.check.CheckProtocol;
import vproxy.base.component.check.HealthCheckConfig;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.svrgroup.Method;
import vproxy.base.component.svrgroup.ServerGroup;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.dns.Resolver;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.h2streamed.H2StreamedClientFDs;
import vproxy.base.selector.wrap.kcp.KCPFDs;
import vproxy.base.selector.wrap.udp.UDPFDs;
import vproxy.base.util.Logger;
import vproxy.base.util.Network;
import vproxy.base.util.callback.BlockCallback;
import vproxy.component.ssl.CertKey;
import vproxy.util.CoreUtils;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxyx.websocks.uot.UdpOverTcpSetup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ConfigProcessor {
    public final ConfigLoader configLoader;
    public final EventLoopGroup hcLoopGroup;
    public final EventLoopGroup workerLoopGroup;
    private final Map<String, ServerGroup> servers = new HashMap<>();
    private final List<CertKey> httpsSniErasureCertKeys = new ArrayList<>();

    public ConfigProcessor(ConfigLoader configLoader, EventLoopGroup hcLoopGroup, EventLoopGroup workerLoopGroup) {
        this.configLoader = configLoader;
        this.hcLoopGroup = hcLoopGroup;
        this.workerLoopGroup = workerLoopGroup;
    }

    public int getSocks5ListenPort() {
        return configLoader.getSocks5ListenPort();
    }

    public int getHttpConnectListenPort() {
        return configLoader.getHttpConnectListenPort();
    }

    public int getSsListenPort() {
        return configLoader.getSsListenPort();
    }

    public String getSsPassword() {
        return configLoader.getSsPassword();
    }

    public int getDnsListenPort() {
        return configLoader.getDnsListenPort();
    }

    public boolean isGateway() {
        return configLoader.isGateway();
    }

    public Map<String, ServerGroup> getServers() {
        return servers;
    }

    public LinkedHashMap<String, List<DomainChecker>> getDomains() {
        return configLoader.getDomains();
    }

    public LinkedHashMap<String, List<DomainChecker>> getProxyResolves() {
        return configLoader.getProxyResolves();
    }

    public LinkedHashMap<String, List<DomainChecker>> getNoProxyDomains() {
        return configLoader.getNoProxyDomains();
    }

    public boolean isDirectRelay() {
        return configLoader.isDirectRelay();
    }

    public IPPort getDirectRelayListen() {
        return configLoader.getDirectRelayListen();
    }

    public Network getDirectRelayIpRange() {
        return configLoader.getDirectRelayIpRange();
    }

    public int getDirectRelayIpBondTimeout() {
        return configLoader.getDirectRelayIpBondTimeout();
    }

    public List<DomainChecker> getHttpsSniErasureDomains() {
        return configLoader.getHttpsSniErasureDomains();
    }

    public String getAutoSignCert() {
        return configLoader.getAutoSignCert();
    }

    public String getAutoSignKey() {
        return configLoader.getAutoSignKey();
    }

    public File getAutoSignWorkingDirectory() {
        return configLoader.getAutoSignWorkingDirectory();
    }

    public List<CertKey> getHttpsSniErasureRelayCertKeys() {
        return httpsSniErasureCertKeys;
    }

    public String getUser() {
        return configLoader.getUser();
    }

    public String getPass() {
        return configLoader.getPass();
    }

    public String getCacertsPath() {
        return configLoader.getCacertsPath();
    }

    public String getCacertsPswd() {
        return configLoader.getCacertsPswd();
    }

    public boolean isVerifyCert() {
        return configLoader.isVerifyCert();
    }

    public boolean isStrictMode() {
        return configLoader.isStrictMode();
    }

    public int getPoolSize() {
        return configLoader.getPoolSize();
    }

    public int getPacServerPort() {
        return configLoader.getPacServerPort();
    }

    private ServerGroup getGroup(String alias) throws Exception {
        if (alias == null) {
            alias = "DEFAULT";
        }
        if (servers.containsKey(alias))
            return servers.get(alias);
        ServerGroup grp = new ServerGroup(alias, hcLoopGroup,
            new HealthCheckConfig(5_000, 30_000, 1, 2, configLoader.isNoHealthCheck() ? CheckProtocol.none : CheckProtocol.tcp)
            , Method.wrr);
        servers.put(alias, grp);
        return grp;
    }

    public void parse() throws Exception {
        List<String> errList = configLoader.validate();
        if (!errList.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean isFirst = true;
            for (String s : errList) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append("\n");
                }
                sb.append(s);
            }
            throw new Exception(sb.toString());
        }

        // handle servers
        var serverListMap = configLoader.getServers();
        String nic = configLoader.getUdpOverTcpNic();
        Logger.alert("enhancing kcp with uot on " + nic);
        KCPFDs kcpFDs;
        if (configLoader.isUdpOverTcpEnabled()) {
            kcpFDs = new KCPFDs(KCPFDs.optionsClientFast4(),
                new UDPFDs(UdpOverTcpSetup.setup(true, -1, nic, workerLoopGroup)));
        } else {
            kcpFDs = KCPFDs.getClientDefault();
        }
        for (String alias : serverListMap.keySet()) {
            ServerList serverList = serverListMap.get(alias);
            for (ServerList.Server svr : serverList.getServers()) {
                addIntoServerGroup(alias, svr, kcpFDs);
            }
        }
        // check for https relay
        if (!configLoader.getHttpsSniErasureCertKeyFiles().isEmpty()) {
            int idx = 0;
            for (List<String> files : configLoader.getHttpsSniErasureCertKeyFiles()) {
                String[] certs = new String[files.size() - 1];
                for (int i = 0; i < certs.length; ++i) {
                    certs[i] = files.get(i);
                }
                String key = files.get(files.size() - 1);
                CertKey certKey = CoreUtils.readCertKeyFromFile("agent.https-sni-erasure.cert-key." + idx, certs, key);
                httpsSniErasureCertKeys.add(certKey);
                ++idx;
            }
        }
        // load cert-key(s) in autoSignWorkingDirectory
        if (configLoader.getAutoSignWorkingDirectory() != null) {
            File[] files = configLoader.getAutoSignWorkingDirectory().listFiles();
            if (files == null) {
                throw new Exception("cannot list files under " + configLoader.getAutoSignWorkingDirectory().getAbsolutePath());
            }
            Set<String> crt = new HashSet<>();
            Set<String> key = new HashSet<>();
            for (File f : files) {
                String name = f.getName();
                if (!name.endsWith(".key") && !name.endsWith(".crt")) {
                    continue;
                }
                String domain = name.substring(0, name.length() - 4);
                if (name.endsWith(".key")) {
                    key.add(domain);
                    if (!crt.contains(domain)) {
                        continue;
                    }
                } else if (name.endsWith(".crt")) {
                    crt.add(domain);
                    if (!key.contains(domain)) {
                        continue;
                    }
                } else {
                    continue;
                }
                loadCertKeyInAutoSignWorkingDirectory(configLoader.getAutoSignWorkingDirectory(), domain);
            }
        }
    }

    private void addIntoServerGroup(String currentAlias, ServerList.Server svr, KCPFDs kcpFDs) throws Exception {
        ServerGroup.ServerHandle handle;
        if (IP.isIpLiteral(svr.host)) {
            IP inet = IP.from(svr.host);
            handle = getGroup(currentAlias).add(svr.toString(), new IPPort(inet, svr.port), 10);
        } else {
            BlockCallback<IP, IOException> cb = new BlockCallback<>();
            Resolver.getDefault().resolveV4(svr.host, cb);
            IP inet = cb.block();
            handle = getGroup(currentAlias).add(svr.toString(), svr.host, new IPPort(inet, svr.port), 10);
        }

        // init streamed fds
        Map<SelectorEventLoop, H2StreamedClientFDs> fds = new HashMap<>();
        if (svr.useKCP) {
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
                    H2StreamedClientFDs h2sFDs = new H2StreamedClientFDs(kcpFDs, l.getSelectorEventLoop(),
                        handle.server);
                    fds.put(l.getSelectorEventLoop(), h2sFDs);
                }
            }
        }
        // this will be used when connection establishes to remote
        // in WebSocksProxyAgentConnectorProvider.java
        // also in HttpDNSServer.java
        handle.data = new SharedData(svr.useSSL, svr.useKCP, fds);
    }

    private void loadCertKeyInAutoSignWorkingDirectory(File autoSignWorkingDirectory, String domain) throws Exception {
        String crt = Path.of(autoSignWorkingDirectory.getAbsolutePath(), domain + ".crt").toString();
        String key = Path.of(autoSignWorkingDirectory.getAbsolutePath(), domain + ".key").toString();
        CertKey ck = CoreUtils.readCertKeyFromFile("agent.auto-sign." + domain, new String[]{crt}, key);
        addCertKeyToAllLists(ck);
    }

    private void addCertKeyToAllLists(CertKey ck) {
        httpsSniErasureCertKeys.add(ck);
    }
}
