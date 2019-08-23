package vserver.util;

import vjson.CharStream;
import vproxy.util.ByteArray;

public class ByteArrayCharStream implements CharStream {
    private final ByteArray array;
    private int idx = -1;

    public ByteArrayCharStream(ByteArray array) {
        this.array = array;
    }

    @Override
    public boolean hasNext(int i) {
        return idx + i < array.length();
    }

    @Override
    public char moveNextAndGet() {
        return (char) array.get(++idx);
    }

    @Override
    public char peekNext(int i) {
        return (char) array.get(idx + i);
    }
}
