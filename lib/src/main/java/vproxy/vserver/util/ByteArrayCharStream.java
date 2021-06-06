package vproxy.vserver.util;

import vjson.CharStream;
import vproxy.base.util.ByteArray;

import java.nio.charset.Charset;

public class ByteArrayCharStream implements CharStream {
    private final char[] chars;
    private int idx = -1;

    public ByteArrayCharStream(ByteArray data, Charset charset) {
        this.chars = new String(data.toJavaArray(), charset).toCharArray();
    }

    @Override
    public boolean hasNext(int i) {
        return idx + i < chars.length;
    }

    @Override
    public char moveNextAndGet() {
        return chars[++idx];
    }

    @Override
    public char peekNext(int i) {
        return chars[idx + i];
    }
}
