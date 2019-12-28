package vproxyx.websocks;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Scanner;

public class ConfigGenerator {
    private ConfigGenerator() {
    }

    private static void download(HttpsURLConnection conn, File save) throws Exception {
        OutputStream file = null;
        try {
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.connect();
            int status = conn.getResponseCode();
            if (status != 200) {
                throw new Exception("request for " + save.getName() + " failed");
            }
            InputStream input = conn.getInputStream();
            // prepare the downloaded file
            file = new FileOutputStream(save);

            byte[] buf = new byte[4096];
            int read;
            while ((read = input.read(buf)) != -1) {
                file.write(buf, 0, read);
            }
            file.flush();
        } finally {
            conn.disconnect();
            if (file != null) {
                file.close();
            }
        }
    }

    private static void downloadGfwListFile(String basedir) throws Exception {
        URI uri = URI.create("https://raw.githubusercontent.com/gfwlist/gfwlist/master/gfwlist.txt");
        HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
        File save = new File(basedir + File.separator + "gfwlist.base64");
        download(conn, save);
    }

    private static boolean getBool(Scanner scanner, boolean dft) throws Exception {
        String str = scanner.nextLine().trim();
        if (str.isEmpty()) {
            return dft;
        }
        if (str.equalsIgnoreCase("y")) {
            return true;
        } else if (str.equalsIgnoreCase("n")) {
            return false;
        } else {
            throw new Exception("Expecting Y or N");
        }
    }

    private static int getPort(Scanner scanner, int dft, boolean allowZero) throws Exception {
        String portStr = scanner.nextLine().trim();
        if (portStr.isEmpty()) {
            return dft;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new Exception("The port you entered is invalid: " + portStr);
        }
        if (port == 0 && allowZero) {
            return 0;
        }
        if (port < 1 || port > 65535) {
            throw new Exception("The port you entered is invalid: " + port);
        }
        return port;
    }

    public static void interactive(String basedir, String configFileName) throws Exception {
        System.out.println("You do not have a config file yet, let's generate one.");
        Scanner scanner = new Scanner(System.in);

        System.out.print("Your username: ");
        String uname = scanner.nextLine().trim();
        if (uname.isEmpty()) {
            throw new Exception("Invalid username");
        }
        System.out.print("Your password: ");
        String upass = scanner.nextLine().trim();
        if (upass.isEmpty()) {
            throw new Exception("Invalid password");
        }

        System.out.println("What's the domain name or ip address of your remote server ?");
        System.out.print("> ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) {
            throw new Exception("Invalid host or ip");
        }

        System.out.println("Did you start the server using the script provided here: https://github.com/wkgcass/vproxy/blob/websocks5/start-vproxy-websocks-proxy-server.sh ?");
        System.out.print("y/[N]> ");
        boolean startScript = getBool(scanner, false);

        int remotePort;
        boolean usingTls;
        boolean usingKcp;

        if (startScript) {
            remotePort = 443;
            usingTls = true;
            usingKcp = true;
        } else {
            System.out.println("What's the port of your WebSocksProxyServer ?");
            System.out.print("[443]> ");
            remotePort = getPort(scanner, 443, false);

            System.out.println("Is your server using TLS/SSL ?");
            System.out.print("[Y]/n> ");
            usingTls = getBool(scanner, true);

            System.out.println("Is KCP enabled on the server side, and you want to access the server via KCP ?");
            System.out.print("[Y]/n> ");
            usingKcp = getBool(scanner, true);
        }

        System.out.println("Do you want to customize other configurations here ?");
        System.out.println("You may refer to manual on github and modify the generated config file later: " +
            "https://github.com/wkgcass/vproxy/blob/master/doc/websocks-agent-example.conf");
        System.out.print("y/[N]> ");
        boolean customize = getBool(scanner, false);

        int socks5Port = 1080;
        int httpConnectPort = 0;
        int ssPort = 0;
        String ssPass = null;
        boolean gateway = false;
        int pacServerPort = 0;

        if (customize) {
            System.out.println("Which port do you want to use to listen for socks5 traffic ?");
            System.out.print("[1080]> ");
            socks5Port = getPort(scanner, 1080, false);

            System.out.println("Which port do you want to use to listen for http connect traffic ? Type in 0 if you do not want to enable.");
            System.out.println("This might be useful when your Android device connect to the agent.");
            System.out.print("[0]> ");
            httpConnectPort = getPort(scanner, 0, true);

            System.out.println("Which port do you want to use to listen for ss traffic ? Type in 0 if you do not want to enable.");
            System.out.println("[0]> ");
            ssPort = getPort(scanner, 0, true);

            if (ssPort != 0) {
                System.out.println("Set the password of your ss server.");
                System.out.println("[agent.ss.password]> ");
                ssPass = scanner.nextLine().trim();
                if (ssPass.isEmpty()) {
                    throw new Exception("The password of ss server should not be empty.");
                }
                System.out.println("The ss server is enabled. You should aware that only aes-256-cfb is supported for now.");
            }

            System.out.println("Do you want to expose the agent to devices in your LAN ?");
            System.out.print("y/[N]> ");
            gateway = getBool(scanner, false);

            System.out.println("Which port do you want to use for the pac server ? Type in 0 if you do not want to launch.");
            if (gateway) {
                System.out.print("[20080]> ");
                pacServerPort = getPort(scanner, 20080, true);
            } else {
                System.out.println("[0]> ");
                pacServerPort = getPort(scanner, 0, true);
            }
        }

        downloadGfwListFile(basedir);

        StringBuilder config = new StringBuilder();
        config.append("#################################################\n");
        config.append("# auto generated by vproxy websocks proxy agent\n");
        config.append("#################################################\n");
        config.append("agent.listen ").append(socks5Port).append("\n");
        if (httpConnectPort != 0) {
            config.append("agent.httpconnect.listen ").append(httpConnectPort).append("\n");
        }
        config.append("\n");
        config.append("proxy.server.auth ").append(uname).append(":").append(upass).append("\n");
        config.append("agent.cert.verify off\n");
        if (gateway) {
            config.append("agent.gateway on\n");
        }
        if (pacServerPort != 0) {
            config.append("agent.gateway.pac.address *:").append(pacServerPort).append("\n");
        }
        if (ssPort != 0) {
            config.append("agent.ss.listen ").append(ssPort).append("\n");
            config.append("agent.ss.password ").append(ssPass).append("\n");
        }
        config.append("\n");
        config.append("proxy.server.list.start\n");
        {
            String kcp = usingKcp ? "kcp:" : "";
            if (usingTls) {
                config.append("websockss:").append(kcp).append("//");
            } else {
                config.append("websocks:").append(kcp).append("//");
            }
            config.append(host).append(":").append(remotePort).append("\n");
        }
        config.append("proxy.server.list.end\n");
        config.append("\n");
        config.append("proxy.domain.list.start\n");
        config.append("[~").append(File.separator).append("gfwlist.base64]\n");
        config.append("proxy.domain.list.end\n");

        String configStr = config.toString();
        System.out.println("generated config is:");
        System.out.println(configStr);

        System.out.println("saving to " + configFileName);
        {
            FileOutputStream outputStream = new FileOutputStream(basedir + File.separator + configFileName);
            byte[] bytes = configStr.getBytes();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
        }
    }
}
