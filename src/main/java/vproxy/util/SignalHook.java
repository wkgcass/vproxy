package vproxy.util;

import sun.misc.Signal;

public class SignalHook {
    private static final SignalHook instance = new SignalHook();

    public static SignalHook getInstance() {
        return instance;
    }

    private void registerSignal(String sig, Runnable r) {
        Signal.handle(new Signal(sig), s -> r.run());
    }

    public void sigInt(Runnable r) {
        registerSignal("INT", r);
    }

    public void sigHup(Runnable r) {
        registerSignal("HUP", r);
    }

    public void sigTerm(Runnable r) {
        registerSignal("TERM", r);
    }

    public void sigUsr1(Runnable r) {
        registerSignal("USR1", r);
    }

    public void sigUsr2(Runnable r) {
        registerSignal("USR2", r);
    }
}
