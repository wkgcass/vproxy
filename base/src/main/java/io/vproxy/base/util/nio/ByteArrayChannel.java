package io.vproxy.base.util.nio;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.vfd.WritableByteStream;

import java.nio.ByteBuffer;

public interface ByteArrayChannel extends ReadableByteStream, WritableByteStream {
    static ByteArrayChannel fromEmpty(int len) {
        return new SimpleByteArrayChannel(ByteArray.allocate(len), 0, 0, len);
    }

    static ByteArrayChannel fromEmpty(byte[] arr) {
        return ByteArrayChannel.fromEmpty(ByteArray.from(arr));
    }

    static ByteArrayChannel fromEmpty(ByteArray arr) {
        return new SimpleByteArrayChannel(arr, 0, 0, arr.length());
    }

    static ByteArrayChannel fromFull(byte[] arr) {
        return new SimpleByteArrayChannel(ByteArray.from(arr), 0, arr.length, 0);
    }

    static ByteArrayChannel from(byte[] arr, int readOff, int writeOff, int writeLen) {
        return from(ByteArray.from(arr), readOff, writeOff, writeLen);
    }

    static ByteArrayChannel from(ByteArray arr, int readOff, int writeOff, int writeLen) {
        return new SimpleByteArrayChannel(arr, readOff, writeOff, writeLen);
    }

    static ByteArrayChannel fromFull(ByteArray arr) {
        return new SimpleByteArrayChannel(arr, 0, arr.length(), 0);
    }

    static ByteArrayChannel zero() {
        return new SimpleByteArrayChannel();
    }

    static ByteArrayChannel fromEmpty(ByteBuffer buf) {
        return new ByteBufferChannel(buf, buf.position(), buf.position(), buf.limit() - buf.position());
    }

    static ByteArrayChannel fromFull(ByteBuffer buf) {
        return new ByteBufferChannel(buf, buf.position(), buf.limit(), 0);
    }

    @Override
    int read(ByteBuffer dst);

    byte read();

    void skip(int n);

    @Override
    int write(ByteBuffer src);

    default int write(ByteArrayChannel src) {
        return write(src, src.used());
    }

    int write(ByteArrayChannel src, int len);

    int free();

    int used();

    void reset();

    default byte[] getBytes() {
        return getArray().toJavaArray();
    }

    ByteArray readableArray();

    ByteArray readAll();

    ByteArray getArray();

    int getWriteOff();

    int getWriteLen();

    int getReadOff();

    void setReadOff(int readOff);
}
