package vproxybase.util;

import vproxybase.util.nio.ByteArrayChannel;

import java.util.Set;

public abstract class AbstractParser<T> {
    protected int state = 0; // initial state is set to 0
    protected T result;
    protected String errorMessage;

    private final byte[] bytes = new byte[1];
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);
    private final Set<Integer> terminateStates;
    private final Set<Integer> terminateRegardlessOfInputStates;

    protected AbstractParser(Set<Integer> terminateStates, Set<Integer> terminateRegardlessOfInputStates) {
        this.terminateStates = terminateStates;
        this.terminateRegardlessOfInputStates = terminateRegardlessOfInputStates;
    }

    public int feed(RingBuffer buffer) {
        while (buffer.used() != 0) {
            chnl.reset();
            buffer.writeTo(chnl);

            byte b = bytes[0];
            state = doSwitch(b);
            if (state == -1) { // parse failed, return -1
                return -1;
            }
            if (terminateRegardlessOfInputStates.contains(state))
                break;
        }
        if (terminateStates.contains(state)) {
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
