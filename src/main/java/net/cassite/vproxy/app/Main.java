package net.cassite.vproxy.app;

import net.cassite.vproxy.app.cmd.handle.param.AddrHandle;
import net.cassite.vproxy.component.app.Shutdown;
import net.cassite.vproxy.component.app.StdIOController;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    private static final String _HELP_STR_ = "" +
        "vproxy: usage java " + Main.class.getName() + " \\" +
        "\n\t\thelp                                         show this message" +
        "\n" +
        "\n\t\tload ${filename}                             load configuration from file" +
        "\n" +
        "\n\t\tresp-controller ${address} ${password}       start the resp-controller, will" +
        "\n\t\t                                             be named as `resp-controller`" +
        "";

    public static void main(String[] args) {
        try {
            Application.create();
        } catch (IOException e) {
            System.err.println("start application failed! " + e);
            e.printStackTrace();
            System.exit(1);
            return;
        }
        // init signal hooks
        Shutdown.init();
        // start ControlEventLoop
        Application.get().controlEventLoop.loop();

        // every other thing should start after the loop

        // load config if specified in args
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            String next = i + 1 < args.length ? args[i + 1] : null;
            String next2 = i + 2 < args.length ? args[i + 2] : null;
            switch (arg) {
                case "help":
                    System.out.println(_HELP_STR_);
                    System.exit(0);
                    return;
                case "load":
                    if (next == null) {
                        System.err.println("invalid system call for `load`: should specify a file name to load");
                        System.exit(1);
                        return;
                    }
                    // handle load, so increase the cursor
                    ++i;
                    try {
                        Shutdown.load(next, new CallbackInMain());
                    } catch (Exception e) {
                        System.err.println("got exception when do pre-loading: " + Utils.formatErr(e));
                        System.exit(1);
                        return;
                    }
                    break;
                case "resp-controller":
                    if (next == null || next2 == null) {
                        System.err.println("invalid system call for `resp-controller`: should specify an address and a password");
                        System.exit(1);
                        return;
                    }
                    // handle resp-controller, so increase the cursor
                    i += 2;
                    InetSocketAddress respCtrlAddr;
                    try {
                        respCtrlAddr = AddrHandle.get(next);
                    } catch (Exception e) {
                        System.err.println("invalid address: " + next);
                        System.exit(1);
                        return;
                    }
                    byte[] pass = next2.getBytes();
                    try {
                        Application.get().respControllerHolder.add("resp-controller", respCtrlAddr, pass);
                    } catch (AlreadyExistException e) {
                        // should not happen
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        System.err.println("start resp-controller failed");
                        System.exit(1);
                        return;
                    }
                    break;
                default:
                    System.err.println("unknown argument `" + arg + "`");
                    System.exit(1);
                    return;
            }
        }

        // start controllers

        // start stdioController
        StdIOController controller = new StdIOController();
        new Thread(controller::start, "StdIOControllerThread").start();
    }

    private static class CallbackInMain extends Callback<String, Throwable> {
        @Override
        protected void onSucceeded(String value) {
            // do nothing if succeeded
        }

        @Override
        protected void onFailed(Throwable err) {
            System.err.println(Utils.formatErr(err));
            System.exit(1);
        }
    }
}
