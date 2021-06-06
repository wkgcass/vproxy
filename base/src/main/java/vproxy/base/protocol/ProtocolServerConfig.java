package vproxy.base.protocol;

public class ProtocolServerConfig {
    int inBufferSize = 16384;
    int outBufferSize = 16384;

    public ProtocolServerConfig setInBufferSize(int inBufferSize) {
        this.inBufferSize = inBufferSize;
        return this;
    }

    public ProtocolServerConfig setOutBufferSize(int outBufferSize) {
        this.outBufferSize = outBufferSize;
        return this;
    }
}
