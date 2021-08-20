package vproxyx.websocks.udpovertcp;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Timer;
import vproxy.vfd.IPPort;

import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class UEntry extends Timer {
    public final IPPort remote;
    public final IPPort local;
    public final LinkedList<ByteArray> queuedPackets = new LinkedList<>();
    private final Consumer<UEntry> cancelFunc;

    public boolean needToSendSyn = true;
    public long seqId;
    public long ackId;

    public UEntry(LocalRemoteIPPort key, Consumer<UEntry> cancelFunc) {
        super(SelectorEventLoop.current(), 90_000);

        this.remote = new IPPort(key.remoteIp, key.remotePort);
        this.local = new IPPort(key.localIp, key.localPort);
        this.cancelFunc = cancelFunc;

        seqId = ThreadLocalRandom.current().nextInt(0xffffff) + 0xaaaaaa;
    }

    @Override
    public void cancel() {
        super.cancel();

        cancelFunc.accept(this);
    }
}
