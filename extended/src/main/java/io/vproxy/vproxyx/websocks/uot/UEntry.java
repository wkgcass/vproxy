package vproxyx.websocks.uot;

import java.util.concurrent.ThreadLocalRandom;

public class UEntry {
    public boolean needToSendSyn = true;
    public long seqId;
    public long ackId;

    public UEntry() {
        seqId = ThreadLocalRandom.current().nextInt(0xffffff) + 0xaaaaaa;
    }
}
