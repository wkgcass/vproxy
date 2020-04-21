package vproxyx;

import vproxy.selector.SelectorEventLoop;
import vproxy.util.Utils;
import vswitch.VXLanAdaptorHandlers;
import vswitch.util.Consts;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;

public class VXLanAdaptor {
    private static final String HELP_STR = "" +
        "usage: switch={} vxlan={} listen={} user={} password={}\n" +
        "       switch:   ip:port of the switch\n" +
        "       vxlan:    ip:port to send vxlan packet\n" +
        "       listen:   ip:port of the listening socket for vxlan packets\n" +
        "       user:     the user of your account\n" +
        "       password: the password for your account" +
        "";

    public static void main0(String[] args) {
        if (args.length == 0) {
            System.out.println(HELP_STR);
            System.exit(1);
            return;
        }
        if (args.length == 1) {
            if (args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h") || args[0].equals("-help")) {
                System.out.println(HELP_STR);
                System.exit(1);
                return;
            }
        }

        String sw = null;
        String vxlan = null;
        String listen = null;
        String user = null;
        String password = null;

        for (String arg : args) {
            if (arg.startsWith("switch=")) {
                sw = arg.substring("switch=".length()).trim();
            } else if (arg.startsWith("vxlan=")) {
                vxlan = arg.substring("vxlan=".length()).trim();
            } else if (arg.startsWith("listen=")) {
                listen = arg.substring("listen=".length()).trim();
            } else if (arg.startsWith("user=")) {
                user = arg.substring("user=".length()).trim();
            } else if (arg.startsWith("password=")) {
                password = arg.substring("password=".length()).trim();
            } else {
                System.out.println("unexpected argument " + arg);
                System.exit(1);
                return;
            }
        }

        if (sw == null) {
            System.out.println("missing argument switch=...");
            System.exit(1);
            return;
        }
        if (vxlan == null) {
            System.out.println("missing argument vxlan=...");
            System.exit(1);
            return;
        }
        if (listen == null) {
            System.out.println("missing argument listen=...");
            System.exit(1);
            return;
        }
        if (user == null) {
            System.out.println("missing argument user=...");
            System.exit(1);
            return;
        }
        if (password == null) {
            System.out.println("missing argument password=...");
            System.exit(1);
            return;
        }

        InetSocketAddress inetSw;
        InetSocketAddress inetVxlan;
        InetSocketAddress inetListen;
        try {
            inetSw = Utils.blockParseL4Addr(sw);
        } catch (Exception e) {
            System.out.println("invalid argument switch=" + sw + " : " + Utils.formatErr(e));
            System.exit(1);
            return;
        }
        try {
            inetVxlan = Utils.blockParseL4Addr(vxlan);
        } catch (Exception e) {
            System.out.println("invalid argument vxlan=" + vxlan + " : " + Utils.formatErr(e));
            System.exit(1);
            return;
        }
        try {
            inetListen = Utils.blockParseL4Addr(listen);
        } catch (Exception e) {
            System.out.println("invalid argument listen=" + listen + " : " + Utils.formatErr(e));
            System.exit(1);
            return;
        }
        if (user.isBlank()) {
            System.out.println("invalid argument user: should not be empty");
            System.exit(1);
            return;
        }
        if (user.length() < 8) {
            user += Consts.USER_PADDING.repeat(8 - user.length());
        }
        byte[] userBytes;
        try {
            userBytes = Base64.getDecoder().decode(user);
        } catch (IllegalArgumentException e) {
            System.out.println("invalid argument user: format invalid");
            System.exit(1);
            return;
        }
        if (userBytes.length != 6) {
            System.out.println("invalid argument user: format invalid");
            System.exit(1);
            return;
        }
        if (password.isBlank()) {
            System.out.println("invalid argument password: should not be empty");
            System.exit(1);
            return;
        }

        SelectorEventLoop loop;
        try {
            loop = SelectorEventLoop.open();
        } catch (IOException e) {
            System.out.println("creating selector event loop failed");
            System.exit(1);
            return;
        }
        try {
            VXLanAdaptorHandlers.launchGeneralAdaptor(loop, inetSw, inetVxlan, inetListen, user, password);
        } catch (IOException e) {
            System.out.println(Utils.formatErr(e));
            System.exit(1);
            return;
        }
        loop.loop();
    }
}
