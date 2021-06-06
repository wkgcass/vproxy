package vproxy.base.processor.httpbin.frame;

import vproxy.base.processor.httpbin.BinaryHttpSubContext;
import vproxy.base.processor.httpbin.HttpFrame;
import vproxy.base.processor.httpbin.HttpFrameType;
import vproxy.base.util.ByteArray;

public class Preface extends HttpFrame {
    private static final ByteArray PREFACE = ByteArray.from("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");

    public Preface() {
        super(HttpFrameType.PREFACE);
    }

    @Override
    public void setFlags(byte flags) { // ignore
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) { // ignore
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteArray serializeH2(BinaryHttpSubContext subCtx) {
        return PREFACE;
    }

    @Override
    public byte serializeFlags() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void toString(StringBuilder sb) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "PRI * HTTP/2.0\\r\\n\\r\\nSM\\r\\n\\r\\n";
    }
}
