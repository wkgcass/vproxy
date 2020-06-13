package vproxybase.processor;

import vfd.IPPort;
import vproxybase.util.ByteArray;

class HeadPayloadContext extends Processor.Context {
}

class HeadPayloadSubContext extends Processor.SubContext {
    int parsedLength = 0;

    public HeadPayloadSubContext(int connId) {
        super(connId);
    }
}

public abstract class HeadPayloadProcessor extends AbstractProcessor<HeadPayloadContext, HeadPayloadSubContext> {
    private final String name;
    private final int head; // length of head in bytes
    private final int off; // the offset of length property in bytes
    private final int len; // the length of length property in bytes
    private final int maxLen;

    protected HeadPayloadProcessor(String name, int head, int off, int len, int maxLen) {
        this.name = name;
        this.head = head;
        this.off = off;
        this.len = len;
        this.maxLen = maxLen;

        if (off + len > head) throw new IllegalArgumentException();
        if (len < 0 || off < 0 || head < 0) throw new IllegalArgumentException();
    }

    @Override
    public HeadPayloadContext init(IPPort ignore) {
        return new HeadPayloadContext();
    }

    @Override
    public HeadPayloadSubContext initSub(HeadPayloadContext headPayloadContext, int id, IPPort ignore) {
        return new HeadPayloadSubContext(id);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    protected void handle(HeadPayloadContext ctx, HeadPayloadSubContext sub) throws Exception {
        switch (sub.step) {
            case 0:
                ByteArray data = wantData(off + len);
                if (data == null)
                    return;

                int parsedLength;
                if (len < 5) { // 1,2,3,4
                    if (len == 1) {
                        parsedLength = data.uint8(off);
                    } else if (len == 2) {
                        parsedLength = data.uint16(off);
                    } else if (len == 3) {
                        parsedLength = data.uint24(off);
                    } else {
                        assert len == 4;
                        parsedLength = data.int32(off);
                    }
                } else {
                    int n = 0;
                    for (int i = 0; i < len; ++i) {
                        int shift = (8 * (len - i - 1));
                        int b = data.uint8(off + i);
                        if (shift > 31) {
                            if (b > 0)
                                throw new Exception("unsupported length: greater than 2^32-1");
                            continue; // otherwise it's 0, no need to consider this byte
                        } else if (shift > 23) {
                            if (b >= 0x80)
                                throw new Exception("unsupported length: greater than 2^32-1");
                        }
                        n |= b << shift;
                    }
                    parsedLength = n;
                }
                if (parsedLength > maxLen)
                    throw new Exception("unsupported length: " + parsedLength + " > " + maxLen);
                sub.parsedLength = parsedLength;
                send(data);

                sub.step = 1;
            case 1:
                HeadPayloadSubContext sendTo = chooseConn();
                if (sub.isFrontend()) {
                    if (sendTo == null) {
                        return;
                    }
                    reuseConn(sendTo.connId);
                }
                wantProxy(head - (off + len) + sub.parsedLength);
                sub.step = 2;
                return;
            case 2:
                frameDone();
                sub.step = 0;
        }
    }
}
