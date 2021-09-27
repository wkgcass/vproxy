package io.vproxy.base.selector.wrap.arqudp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.util.function.Consumer;

public abstract class ArqUDPHandler {
    protected final Consumer<ByteArrayChannel> emitter;

    protected ArqUDPHandler(Consumer<ByteArrayChannel> emitter) {
        this.emitter = emitter;
    }

    /**
     * @param input the network level bytes
     * @return the parsed bytes
     */
    abstract public ByteArray parse(ByteArrayChannel input) throws IOException;

    /**
     * @param input the application level bytes
     */
    abstract public void write(ByteArray input) throws IOException;

    abstract public int writableLen();

    abstract public void clock(long ts) throws IOException;

    abstract public int clockInterval();
}
