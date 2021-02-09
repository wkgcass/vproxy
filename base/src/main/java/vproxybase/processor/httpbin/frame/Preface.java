package vproxybase.processor.httpbin.frame;

import vproxybase.processor.httpbin.BinaryHttpSubContext;
import vproxybase.processor.httpbin.HttpFrame;
import vproxybase.processor.httpbin.HttpFrameType;
import vproxybase.util.ByteArray;

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
