package vproxy.util;

import java.io.InputStream;

public class ArrayInputStream extends InputStream {
    private final ByteArray array;
    private int cursor = 0;

    private ArrayInputStream(ByteArray array) {
        this.array = array;
    }

    public static ArrayInputStream from(ByteArray array) {
        return new ArrayInputStream(array);
    }

    @Override
    public int read() {
        if (cursor >= array.length()) {
            return -1;
        }
        return array.uint8(cursor++);
    }

    @Override
    public int available() {
        return array.length() - cursor;
    }
}
