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

    private static String getKcpClientProgram(String os, int arch, String baseDir) throws Exception {
        String name;
        //noinspection IfCanBeSwitch
        if (os.equals("win")) {
            if (arch == 32) {
                name = "client_windows_386.exe";
            } else if (arch == 64) {
                name = "client_windows_amd64.exe";
            } else {
                throw new Exception("should not reach here");
            }
        } else if (os.equals("mac")) {
            if (arch == 32) {
                throw new Exception("There should be no arch=32bit mac any more.");
            } else if (arch == 64) {
                name = "client_darwin_amd64";
            } else {
                throw new Exception("should not reach here");
            }
        } else if (os.equals("linux")) {
            if (arch == 32) {
                name = "client_linux_386";
            } else if (arch == 64) {
                name = "client_linux_amd64";
            } else {
                throw new Exception("should not reach here");
            }
        } else {
            throw new Exception("should not reach here");
        }

        File save = new File(baseDir + File.separator + name);

        URI uri = URI.create("https://github.com/wkgcass/kcptun-extract/releases/download/v20190611/" + name);
        HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
        download(conn, save);
        //noinspection ResultOfMethodCallIgnored
        save.setExecutable(true);
        return save.getAbsolutePath();
    }

    private static void downloadPacFile(String basedir) throws Exception {
        URI uri = URI.create("https://raw.githubusercontent.com/petronny/gfwlist2pac/master/gfwlist.pac");
        HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
        File save = new File(basedir + File.separator + "gfwlist.pac");
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
        int kcpPort;
        String kcpClientName = null;

        if (startScript) {
            remotePort = 443;
            usingTls = true;
            kcpPort = 8443;
        } else {
            System.out.println("What's the port of your WebSocksProxyServer ?");
            System.out.print("[443]> ");
            remotePort = getPort(scanner, 443, false);

            System.out.println("Is your server using TLS/SSL ?");
            System.out.print("[Y]/n> ");
            usingTls = getBool(scanner, true);

            System.out.println("What's the KCP port of your server ? Type in 0 if you are not using KCP.");
            System.out.print("[0]> ");
            kcpPort = getPort(scanner, 0, true);
        }

        if (kcpPort != 0) {
            String kcptunDirStr = basedir + File.separator + "kcptun";
            File kcptunDir = new File(kcptunDirStr);
            if (kcptunDir.exists() && !kcptunDir.isDirectory()) {
                throw new Exception(kcptunDirStr + " is not a directory");
            }
            if (!kcptunDir.exists()) {
                if (!kcptunDir.mkdir()) {
                    throw new Exception("make directory failed: " + kcptunDirStr);
                }
            }
            File[] files = kcptunDir.listFiles((dir, name) -> name.startsWith("client_"));
            if (files == null) {
                throw new Exception("unexpected null for filtering files in " + kcptunDirStr);
            }
            if (files.length > 0) {
                kcpClientName = files[0].getAbsolutePath();
            } else {
                System.out.println("Trying to get kcptun...");
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    os = "win";
                } else if (os.contains("mac")) {
                    os = "mac";
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    os = "linux";
                } else {
                    System.err.println("We cannot identify your OS: " + os + ", disabling KCP support.");
                    os = null;
                    kcpPort = 0;
                }
                String archStr = System.getProperty("os.arch");
                int arch;
                if (archStr.contains("64")) {
                    arch = 64;
                } else {
                    arch = 32;
                }
                if (kcpPort != 0) {
                    kcpClientName = getKcpClientProgram(os, arch, kcptunDirStr);
                }
            }
        }

        System.out.println("Do you want to customize other configurations here ?");
        System.out.println("You may refer to manual on github and modify the generated config file later.");
        System.out.print("y/[N]> ");
        boolean customize = getBool(scanner, false);

        int socks5Port = 1080;
        int httpConnectPort = 0;
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

        downloadPacFile(basedir);

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
        config.append("\n");
        config.append("proxy.server.list.start\n");
        {
            if (usingTls) {
                config.append("websockss://");
            } else {
                config.append("websocks://");
            }
            config.append(host).append(":").append(remotePort);
            if (kcpPort != 0) {
                config.append(" ").append(kcpClientName).append(" -r $SERVER_IP:8$SERVER_PORT -l 127.0.0.1:$LOCAL_PORT -mode fast3 -nocomp -autoexpire 900 -sockbuf 16777216 -dscp 46");
            }
            config.append("\n");
        }
        config.append("proxy.server.list.end\n");
        config.append("\n");
        config.append("proxy.domain.list.start\n");
        config.append("[~").append(File.separator).append("gfwlist.pac]\n");
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
