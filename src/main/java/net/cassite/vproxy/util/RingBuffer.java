package net.cassite.vproxy.util;

import net.cassite.vproxy.util.ringbuffer.SimpleRingBuffer;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 0                                     CAP
 * first
 * ....->[           free space           ]
 * [sPos,ePos-----------------------------]
 * pace       ]->           ->[      free s
 * [---------sPos---------ePos------------]
 * then
 * [  free space  ]->                      ->
 * [------------ePos--------------------sPos
 * then
 * .........->[   free space    ]->
 * [------ePos----------------sPos--------]
 * maybe we have
 * ..........................->(cannot write into buf any more)
 * [----------------------ePos,sPos--------]
 */
public interface RingBuffer {
    SimpleRingBuffer EMPTY_BUFFER = allocate(0);

    static SimpleRingBuffer allocateDirect(int cap) {
        return SimpleRingBuffer.allocateDirect(cap);
    }

    static SimpleRingBuffer allocate(int cap) {
        return SimpleRingBuffer.allocate(cap);
    }

    default int storeBytesFrom(ByteBuffer byteBuffer) {
        throw new UnsupportedOperationException();
    }

    default int storeBytesFrom(ByteArrayChannel channel) {
        try {
            return storeBytesFrom((ReadableByteChannel) channel);
        } catch (IOException e) {
            // it's memory operation, should not happen
            throw new RuntimeException(e);
        }
    }

    int storeBytesFrom(ReadableByteChannel channel) throws IOException;

    default int writeTo(ByteArrayChannel channel) {
        try {
            return writeTo((WritableByteChannel) channel);
        } catch (IOException e) {
            // it's memory operation, should not raise error
            throw new RuntimeException(e);
        }
    }

    default int writeTo(WritableByteChannel channel) throws IOException {
        return writeTo(channel, Integer.MAX_VALUE);
    }

    int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException;

    default int writeToDatagramChannel(DatagramChannel channel, SocketAddress sockAddr) throws IOException {
        return writeToDatagramChannel(channel, sockAddr, Integer.MAX_VALUE);
    }

    default int writeToDatagramChannel(DatagramChannel channel, SocketAddress sockAddr, int maxBytesToWrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    int free();

    int used();

    int capacity();

    default byte[] getBytes() {
        throw new UnsupportedOperationException();
    }

    void addHandler(RingBufferETHandler h);

    void removeHandler(RingBufferETHandler h);

    void close();

    void clean();

    void clear();

    class RejectSwitchException extends Exception {
        public RejectSwitchException(String msg) {
            super(msg);
        }
    }

    default RingBuffer switchBuffer(RingBuffer buf) throws RejectSwitchException {
        // default: simply use the new buffer when need to switch
        return buf;
    }
}
