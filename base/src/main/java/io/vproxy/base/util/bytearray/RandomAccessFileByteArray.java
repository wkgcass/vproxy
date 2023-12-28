package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class RandomAccessFileByteArray extends AbstractByteArray implements ByteArray, AutoCloseable {
    private final RandomAccessFile file;
    private final FileChannel channel;
    private final long length;
    private int currentPos = 0;

    public RandomAccessFileByteArray(String path) throws FileNotFoundException {
        this(new File(path));
    }

    public RandomAccessFileByteArray(Path path) throws FileNotFoundException {
        this(path.toFile());
    }

    public RandomAccessFileByteArray(File file) throws FileNotFoundException {
        this(new RandomAccessFile(file, "rw"));
    }

    public RandomAccessFileByteArray(RandomAccessFile file) {
        this.file = file;
        this.channel = file.getChannel();
        try {
            this.length = file.length();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkIndexAndBounds(int idx, int len) {
        if (idx < 0)
            throw new IllegalArgumentException("index " + idx + " < 0");
        if (idx >= length)
            throw new IllegalArgumentException("index " + idx + " >= length " + length);
        if (len < 0)
            throw new IllegalArgumentException("length " + len + " < 0");
        if (idx + len > length)
            throw new IllegalArgumentException("index+len " + (idx + len) + " > length " + length);
    }

    private void seek(int pos) throws IOException {
        if (currentPos == pos) {
            return;
        }
        file.seek(pos);
        currentPos = pos;
    }

    @Override
    public byte get(int idx) {
        checkIndexAndBounds(idx, 1);
        try {
            seek(idx);
            byte res = (byte) file.read();
            currentPos += 1;
            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ByteArray set(int idx, byte value) {
        checkIndexAndBounds(idx, 1);
        try {
            seek(idx);
            file.write(value);
            currentPos += 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public int length() {
        return (int) length;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        checkIndexAndBounds(off, len);
        try {
            seek(off);
            var oldLim = dst.limit();
            dst.limit(dst.position() + len);
            channel.read(dst);
            dst.limit(oldLim);
            currentPos += len;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        checkIndexAndBounds(off, len);
        try {
            seek(off);
            var oldLim = src.limit();
            src.limit(src.position() + len);
            channel.write(src);
            src.limit(oldLim);
            currentPos += len;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        try {
            seek(srcOff);
            file.read(dst, dstOff, srcLen);
            currentPos += srcLen;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RandomAccessFile getFile() {
        return file;
    }

    public FileChannel getChannel() {
        return channel;
    }

    @Override
    public void close() throws Exception {
        file.close();
    }
}
