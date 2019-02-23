package net.cassite.vproxyx.websocks5;

import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;
import net.cassite.vproxy.util.Utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class ConfigProcessor {
    public final String fileName;
    public final ServerGroup group;
    private int listenPort = 1080;
    private List<Pattern> domains = new LinkedList<>();
    private String user;
    private String pass;

    public ConfigProcessor(String fileName, ServerGroup group) {
        this.fileName = fileName;
        this.group = group;
    }

    public int getListenPort() {
        return listenPort;
    }

    public List<Pattern> getDomains() {
        return domains;
    }

    public String getUser() {
        return user;
    }

    public String getPass() {
        return pass;
    }

    public void parse() throws Exception {
        FileInputStream inputStream = new FileInputStream(fileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        int step = 0;
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
                } else if (line.equals("proxy.server.list.start")) {
                    step = 1; // retrieving server list
                } else if (line.equals("proxy.domain.list.start")) {
                    step = 2;
                } else {
                    throw new Exception("unknown line: " + line);
                }
            } else if (step == 1) {
                if (line.equals("proxy.server.list.end")) {
                    step = 0; // return to normal state
                    continue;
                }
                if (!line.startsWith("websocks5://") && !line.startsWith("websocks5s://")) {
                    throw new Exception("unknown protocol: " + line);
                }

                boolean useSSL = line.startsWith("websocks5s");
                line = line.substring(useSSL ? "websocks5s://".length() : "websocks5://".length());

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
                    handle = group.add(hostPart, new InetSocketAddress(inet, port), InetAddress.getByName("0.0.0.0"), 10);
                } else if (Utils.isIpv6(hostPart)) {
                    InetAddress inet = InetAddress.getByName(hostPart);
                    handle = group.add(hostPart, new InetSocketAddress(inet, port), InetAddress.getByName("::"), 10);
                } else {
                    BlockCallback<InetAddress, IOException> cb = new BlockCallback<>();
                    Resolver.getDefault().resolveV4(hostPart, cb);
                    InetAddress inet = cb.block();
                    handle = group.add(hostPart, hostPart, new InetSocketAddress(inet, port), InetAddress.getByName("0.0.0.0"), 10);
                }

                // this will be used when connection establishes to remote
                // in WebSocks5ProxyAgentConnectorProvider.java
                handle.data = useSSL;
            } else {
                //noinspection ConstantConditions
                assert step == 2;
                if (line.equals("proxy.domain.list.end")) {
                    step = 0;
                    continue;
                }
                String regexp;
                if (line.startsWith("/") && line.endsWith("/")) {
                    regexp = line.substring(1, line.length() - 1);
                } else {
                    regexp = ".*" + line.replaceAll("\\.", "\\\\.") + "$";
                }
                Pattern pattern = Pattern.compile(regexp);
                domains.add(pattern);
            }
        }

        if (user == null || pass == null)
            throw new Exception("proxy.server.auth not present");
    }
}
