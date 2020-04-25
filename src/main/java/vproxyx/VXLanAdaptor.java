package vproxyx;

import vfd.DatagramFD;
import vfd.FDProvider;
import vfd.FDs;
import vfd.posix.PosixFDs;
import vfd.posix.TunTapDatagramFD;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.Utils;
import vswitch.VXLanAdaptorHandlers;
import vswitch.util.Consts;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;

public class VXLanAdaptor {
    private static final String HELP_STR = "" +
        "usage: switch={} user={} password={} from={vxlan|tap}\n" +
        "    if from = vxlan:\n" +
        "       vxlan={} listen={}\n" +
        "    if from = tap\n" +
        "       dev={} [no-pi]\n" +
        "arguments:" +
        "       switch:   ip:port of the switch\n" +
        "       user:     the user of your account\n" +
        "       password: the password for your account\n" +
        "       from:     the source device, may set to vxlan or tap\n" +
        "       vxlan:    ip:port to send vxlan packet\n" +
        "       listen:   ip:port of the listening socket for vxlan packets\n" +
        "       dev:      the tap device name or pattern of the tap device\n" +
        "       no-pi:    no packet information" +
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
        String user = null;
        String password = null;
        String from = null;

        String vxlan = null;
        String listen = null;

        String dev = null;
        boolean noPi = false;

        for (String arg : args) {
            if (arg.startsWith("switch=")) {
                sw = arg.substring("switch=".length()).trim();
            } else if (arg.startsWith("user=")) {
                user = arg.substring("user=".length()).trim();
            } else if (arg.startsWith("password=")) {
                password = arg.substring("password=".length()).trim();
            } else if (arg.startsWith("from=")) {
                from = arg.substring("from=".length());
            } else if (arg.startsWith("vxlan=")) {
                vxlan = arg.substring("vxlan=".length()).trim();
            } else if (arg.startsWith("listen=")) {
                listen = arg.substring("listen=".length()).trim();
            } else if (arg.startsWith("dev=")) {
                dev = arg.substring("dev=".length()).trim();
            } else if (arg.equals("no-pi")) {
                noPi = true;
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
        if (from == null) {
            System.out.println("missing argument from=...");
            System.exit(1);
            return;
        }

        InetSocketAddress inetSw;
        byte[] userBytes;
        try {
            inetSw = Utils.blockParseL4Addr(sw);
        } catch (Exception e) {
            System.out.println("invalid argument switch=" + sw + " : " + Utils.formatErr(e));
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

        if (from.equals("vxlan")) {
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
            InetSocketAddress inetVxlan;
            InetSocketAddress inetListen;

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
            try {
                VXLanAdaptorHandlers.launchGeneralAdaptor(loop, inetSw, inetVxlan, inetListen, user, password);
            } catch (IOException e) {
                System.out.println(Utils.formatErr(e));
                System.exit(1);
                return;
            }
        } else if (from.equals("tap")) {
            if (dev == null || dev.isBlank()) {
                System.out.println("missing argument tap=...");
                System.exit(1);
                return;
            }
            if (dev.length() > 10) {
                System.out.println("dev pattern too long, should <= 10");
                System.exit(1);
                return;
            }
            int flags = TunTapDatagramFD.IFF_TAP;
            if (noPi) {
                flags |= TunTapDatagramFD.IFF_NO_PI;
            }
            FDs fds = FDProvider.get().getProvided();
            if (!(fds instanceof PosixFDs)) {
                System.out.println("tap is not supported by " + fds);
                System.exit(1);
                return;
            }
            PosixFDs posixFDs = (PosixFDs) fds;
            TunTapDatagramFD fd;
            try {
                fd = posixFDs.openTunTap(dev, flags);
                fd.configureBlocking(false);
            } catch (IOException e) {
                System.out.println("opening tap device failed: " + Utils.formatErr(e));
                System.exit(1);
                return;
            }
            DatagramFD switchSock;
            try {
                switchSock = FDProvider.get().openDatagramFD();
                switchSock.configureBlocking(false);
                switchSock.connect(inetSw);
            } catch (IOException e) {
                System.out.println("opening the socket to switch failed: " + Utils.formatErr(e));
                System.exit(1);
                return;
            }
            try {
                VXLanAdaptorHandlers.launchGeneralAdaptor(loop, flags, switchSock, fd, fd, user, password);
            } catch (IOException e) {
                System.out.println(Utils.formatErr(e));
                System.exit(1);
                return;
            }
        }

        loop.loop();
    }
}
