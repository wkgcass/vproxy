package net.cassite.vproxy.util;

import java.io.IOException;

public abstract class AbstractParser<T> {
    protected int state = 0; // initial state is set to 0
    protected T result;
    protected String errorMessage;

    private final byte[] bytes = new byte[1];
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);

    public int feed(RingBuffer buffer) {
        while (buffer.used() != 0) {
            chnl.reset();
            try {
                buffer.writeTo(chnl);
            } catch (IOException e) {
                // should not happen, it's memory operation
                return -1;
            }

            byte b = bytes[0];
            state = doSwitch(b);
            if (state == -1) { // parse failed, return -1
                return -1;
            }
        }
        if (state == 9) {
            return 0;
        }
        return -1; // indicating that the parser want more data
    }

    protected abstract int doSwitch(byte b);

    public String getErrorMessage() {
        return errorMessage;
    }

    public T getResult() {
        return result;
    }
}
