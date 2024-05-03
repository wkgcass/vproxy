package io.vproxy.base.processor;

import io.vproxy.base.util.ByteArray;

public abstract class HeadPayloadProcessor extends OOProcessor<HeadPayloadProcessor.HeadPayloadContext, HeadPayloadProcessor.HeadPayloadSubContext> {
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
    public HeadPayloadContext init(ContextInitParams params) {
        return new HeadPayloadContext();
    }

    @Override
    public HeadPayloadSubContext initSub(SubContextInitParams<HeadPayloadContext> params) {
        return new HeadPayloadSubContext(params.ctx(), params.id(), params.delegate(), head, off, len, maxLen);
    }

    @Override
    public String name() {
        return this.name;
    }

    public static class HeadPayloadContext extends OOContext<HeadPayloadSubContext> {
    }

    public static class HeadPayloadSubContext extends OOSubContext<HeadPayloadContext> {
        private final int off; // the offset of length property in bytes
        private final int len; // the length of length property in bytes
        private final int maxLen; // the max supported length

        private final int handleLen;
        private final int proxyBaseLen;

        private ProcessorTODO nextProcessorTODO;

        public HeadPayloadSubContext(HeadPayloadContext headPayloadContext, int connId, ConnectionDelegate delegate,
                                     int head, int off, int len, int maxLen) {
            super(headPayloadContext, connId, delegate);
            this.off = off;
            this.len = len;
            this.maxLen = maxLen;

            handleLen = off + len;
            proxyBaseLen = head - handleLen;

            initProcessorTODO();
        }

        private void initProcessorTODO() {
            nextProcessorTODO = ProcessorTODO.create();
            nextProcessorTODO.len = handleLen;
            nextProcessorTODO.mode = Mode.handle;
            nextProcessorTODO.feed = this::feed;
        }

        @Override
        public ProcessorTODO process() {
            return nextProcessorTODO;
        }

        private HandleTODO buildFeedResult(ByteArray data, int parsedLength) {
            nextProcessorTODO = ProcessorTODO.createProxy();
            nextProcessorTODO.len = proxyBaseLen + parsedLength;
            nextProcessorTODO.proxyTODO.proxyDone = this::proxyDone;

            HandleTODO handleTODO = HandleTODO.create();
            handleTODO.send = data;
            if (isFrontend()) {
                nextProcessorTODO.proxyTODO.connTODO = ConnectionTODO.create();

                handleTODO.connTODO = ConnectionTODO.create();
                handleTODO.connTODO.connId = -1;
                handleTODO.connTODO.chosen = subCtx -> nextProcessorTODO.proxyTODO.connTODO.connId = subCtx.connId;
            }
            return handleTODO;
        }

        private HandleTODO feed(ByteArray data) throws Exception {
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
                return buildFeedResult(data, parsedLength);
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
            return buildFeedResult(data, parsedLength);
        }

        private ProxyDoneTODO proxyDone() {
            initProcessorTODO();
            return ProxyDoneTODO.createFrameEnds();
        }

        @Override
        public HandleTODO connected() {
            return null; // send nothing when connected
        }

        @Override
        public HandleTODO remoteClosed() {
            return null; // return nothing
        }

        @Override
        public DisconnectTODO disconnected(boolean exception) {
            return null; // unable to handle this condition
        }
    }
}
