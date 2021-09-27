package io.vproxy.base.util.codec;

import io.vproxy.base.util.RingBuffer;

import java.util.Set;

public abstract class AbstractDecoder<T> {
    protected int state = 0; // initial state is set to 0
    protected T result;
    protected String errorMessage;

    private final Set<Integer> terminateStates;
    private final Set<Integer> terminateRegardlessOfInputStates;

    protected AbstractDecoder(Set<Integer> terminateStates, Set<Integer> terminateRegardlessOfInputStates) {
        this.terminateStates = terminateStates;
        this.terminateRegardlessOfInputStates = terminateRegardlessOfInputStates;
    }

    public int feed(RingBuffer inBuffer) {
        while (inBuffer.used() > 0) {
            state = doDecode(inBuffer);
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

    protected abstract int doDecode(RingBuffer buffer);

    public String getErrorMessage() {
        return errorMessage;
    }

    public T getResult() {
        return result;
    }
}
