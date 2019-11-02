package vproxy.util.io;

import vproxy.util.ByteArray;

import java.io.OutputStream;

public class ArrayOutputStream extends OutputStream {
    private final ByteArray array;
    private int curosr = 0;

    private ArrayOutputStream(ByteArray array) {
        this.array = array;
    }

    public static ArrayOutputStream to(ByteArray array) {
        return new ArrayOutputStream(array);
    }

    @Override
    public void write(int b) {
        array.set(curosr++, (byte) b);
    }

    public ByteArray get() {
        if (curosr == 0)
            return ByteArray.from(new byte[0]);
        ByteArray ret = array.sub(0, curosr).copy();
        curosr = 0;
        return ret;
    }
}
