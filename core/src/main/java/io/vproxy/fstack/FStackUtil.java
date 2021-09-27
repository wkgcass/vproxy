package vproxy.fstack;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.VFDConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class FStackUtil {
    private static IFStack fstack;
    private static SelectorEventLoop loop;

    public static void init() throws IOException {
        // try to load vproxy fstack lib
        try {
            Utils.loadDynamicLibrary("vfdfstack");
            fstack = new FStack();
            Logger.alert("RUNNING F-STACK");
        } catch (UnsatisfiedLinkError e) {
            VFDConfig.vfdlibname = "vfdposix";
            Logger.error(LogType.ALERT, "loading vfdfstack failed, fallback to MockFStack impl");
            assert Logger.printStackTrace(e);
            fstack = new MockFStack();
            Logger.alert("running Mock f-stack, an infinite java loop");
        }

        // init
        List<String> args = Arrays.stream(VFDConfig.fstack.split(" ")).filter(s -> !s.isBlank()).map(String::trim).collect(Collectors.toCollection(LinkedList::new));
        args.add(0, "vproxy" /*this is a placeholder for program name in c argv list*/);
        fstack.ff_init(args);
        loop = SelectorEventLoop.open();
        loop._bindThread0();
    }

    public static void run() {
        fstack.ff_run(() -> loop.onePoll());
    }
}
