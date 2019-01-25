package net.cassite.vproxy.protocol;

public class ProtocolServerConfig {
    int inBufferSize;
    int outBufferSize;

    public ProtocolServerConfig setInBufferSize(int inBufferSize) {
        this.inBufferSize = inBufferSize;
        return this;
    }

    public ProtocolServerConfig setOutBufferSize(int outBufferSize) {
        this.outBufferSize = outBufferSize;
        return this;
    }
}
