package vproxy.processor;

import vproxy.util.ByteArray;

import java.net.InetSocketAddress;

class HeadPayloadContext extends OOContext<HeadPayloadSubContext> {
    int nextConnId = -1;

    @Override
    public int connection(HeadPayloadSubContext front) {
        return nextConnId;
    }

    @Override
    public void chosen(HeadPayloadSubContext front, HeadPayloadSubContext subCtx) {
        nextConnId = subCtx.connId;
    }
}

class HeadPayloadSubContext extends OOSubContext<HeadPayloadContext> {
    private final int off; // the offset of length property in bytes
    private final int len; // the length of length property in bytes
    private final int maxLen; // the max supported length

    private final int handleLen;
    private final int proxyBaseLen;

    private boolean expectingHead = true;
    private int parsedLength = 0;

    public HeadPayloadSubContext(HeadPayloadContext headPayloadContext, int connId,
                                 int head, int off, int len, int maxLen) {
        super(headPayloadContext, connId);
        this.off = off;
        this.len = len;
        this.maxLen = maxLen;

        handleLen = off + len;
        proxyBaseLen = head - handleLen;
    }

    @Override
    public Processor.Mode mode() {
        return expectingHead ? Processor.Mode.handle : Processor.Mode.proxy;
    }

    @Override
    public boolean expectNewFrame() {
        // proxy length 0 also means that the previous frame is finished
        return expectingHead || (proxyBaseLen + parsedLength == 0);
    }

    @Override
    public int len() {
        return expectingHead ? handleLen : proxyBaseLen + parsedLength;
    }

    @Override
    public ByteArray feed(ByteArray data) throws Exception {
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
            expectingHead = false;
            return data;
        }

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
        if (n > maxLen)
            throw new Exception("unsupported length: " + n + " > " + maxLen);
        parsedLength = n;
        expectingHead = false;
        return data;
    }

    @Override
    public ByteArray produce() {
        return null; // always produce nothing
    }

    @Override
    public void proxyDone() {
        if (connId == 0)
            ctx.nextConnId = -1;
        expectingHead = true;
    }

    @Override
    public ByteArray connected() {
        return null; // send nothing when connected
    }
}

public abstract class HeadPayloadProcessor extends OOProcessor<HeadPayloadContext, HeadPayloadSubContext> {
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
    public HeadPayloadContext init(InetSocketAddress ignore) {
        return new HeadPayloadContext();
    }

    @Override
    public HeadPayloadSubContext initSub(HeadPayloadContext headPayloadContext, int id, InetSocketAddress ignore) {
        return new HeadPayloadSubContext(headPayloadContext, id, head, off, len, maxLen);
    }

    @Override
    public String name() {
        return this.name;
    }
}
