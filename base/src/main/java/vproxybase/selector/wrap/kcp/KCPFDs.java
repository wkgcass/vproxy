package vproxybase.selector.wrap.kcp;

import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxybase.selector.wrap.udp.UDPFDs;

import java.io.IOException;

public class KCPFDs implements ArqUDPBasedFDs {
    private static final KCPFDs instanceFast4;
    private static final KCPFDs instanceFast3 = new KCPFDs(new KCPHandler.KCPOptions());
    private static final KCPFDs instanceFast2;
    private static final KCPFDs instanceFast1;
    private static final KCPFDs instanceNormal;

    private static final KCPFDs instanceClientFast4;
    private static final KCPFDs instanceClientFast3;

    static {
        {
            // https://github.com/xtaci/kcptun/blob/1b72bf39d8ef6d83b28facdbe4fb8a21fddbd5ee/server/main.go#L369
            var opts = new KCPHandler.KCPOptions();
            opts.interval = 20;
            instanceFast2 = new KCPFDs(opts);
        }
        {
            var opts = new KCPHandler.KCPOptions();
            opts.sndWnd = 128;
            // client usually won't sent too much data
            // and client network quality is usually better than the server side
            instanceClientFast3 = new KCPFDs(opts);
        }
        {
            // https://github.com/xtaci/kcptun/blob/1b72bf39d8ef6d83b28facdbe4fb8a21fddbd5ee/server/main.go#L367
            var opts = new KCPHandler.KCPOptions();
            opts.nodelay = false;
            opts.interval = 30;
            opts.rxMinRto = 60;
            instanceFast1 = new KCPFDs(opts);
        }
        {
            // https://github.com/xtaci/kcptun/blob/1b72bf39d8ef6d83b28facdbe4fb8a21fddbd5ee/server/main.go#L365
            var opts = new KCPHandler.KCPOptions();
            opts.nodelay = false;
            opts.interval = 40;
            opts.rxMinRto = 100;
            instanceNormal = new KCPFDs(opts);
        }

        {
            // very aggressive strategy
            var opts = new KCPHandler.KCPOptions();
            opts.resend = 1;
            opts.interval = 5;
            opts.clockInterval = 5;
            instanceFast4 = new KCPFDs(opts);
        }

        {
            // very aggressive strategy
            var opts = new KCPHandler.KCPOptions();
            opts.resend = 1;
            opts.sndWnd = 256;
            opts.interval = 5;
            opts.clockInterval = 5;
            instanceClientFast4 = new KCPFDs(opts);
        }
    }

    private final KCPHandler.KCPOptions opts;

    public KCPFDs(KCPHandler.KCPOptions opts) {
        this.opts = opts;
    }

    public static KCPFDs getDefault() {
        return instanceFast4;
    }

    public static KCPFDs getClientDefault() {
        return instanceClientFast4;
    }

    public static KCPFDs getFast3() {
        return instanceFast3;
    }

    public static KCPFDs getClientFast3() {
        return instanceClientFast3;
    }

    public static KCPFDs getFast2() {
        return instanceFast2;
    }

    public static KCPFDs getFast1() {
        return instanceFast1;
    }

    public static KCPFDs getNormal() {
        return instanceNormal;
    }

    @Override
    public KCPServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException {
        return new KCPServerSocketFD(
            UDPFDs.get().openServerSocketFD(loop),
            loop, opts
        );
    }

    @Override
    public KCPSocketFD openSocketFD(SelectorEventLoop loop) throws IOException {
        return new KCPSocketFD(
            UDPFDs.get().openSocketFD(loop),
            loop, opts
        );
    }
}
