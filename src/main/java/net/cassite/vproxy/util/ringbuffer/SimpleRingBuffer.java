package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.util.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class SimpleRingBuffer implements RingBuffer {
    private final boolean isDirect;
    private final ByteBuffer buffer;
    private int ePos; // end pos
    private int sPos; // start pos
    private int cap;
    private boolean ePosIsAfterSPos = true; // true then end is limit, otherwise start is limit
    private boolean closed = false;

    private boolean operating = false;
    private Set<RingBufferETHandler> handler = new HashSet<>();
    private Set<RingBufferETHandler> handlerToAdd = new HashSet<>();
    private Set<RingBufferETHandler> handlerToRemove = new HashSet<>();

    public SimpleRingBuffer(boolean isDirect, ByteBuffer buffer) {
        this.isDirect = isDirect;
        this.buffer = buffer;
        this.cap = buffer.capacity();
    }

    private int storeLimit() {
        if (ePosIsAfterSPos) {
            return cap - ePos; // we can store until capacity
        } else {
            return sPos - ePos; // we can store until the `start` pos
        }
    }

    private int retrieveLimit(int sPos, boolean ePosIsLimit) {
        if (ePosIsLimit) {
            return ePos - sPos; // we can retrieve until end
        } else {
            return cap - sPos; // we can retrieve until capacity
        }
    }

    private int retrieveLimit() {
        return retrieveLimit(this.sPos, this.ePosIsAfterSPos);
    }

    public int storeBytesFrom(ByteBuffer byteBuffer) {
        try {
            return operateOnByteBufferStoreIn(b -> {
                int lim = b.limit() - b.position();
                int oldLimit = byteBuffer.limit();
                if (byteBuffer.remaining() > lim) {
                    // make sure it won't overflow
                    byteBuffer.limit(byteBuffer.position() + lim);
                } // otherwise it's safe to write
                buffer.put(byteBuffer);
                byteBuffer.limit(oldLimit); // restore the limit
                return true;
            });
        } catch (IOException e) {
            Logger.shouldNotHappen("it's memory operation, should not have IOException");
            throw new RuntimeException(e);
        }
    }

    /**
     * @return may return -1 for EOF
     */
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        return operateOnByteBufferStoreIn(b -> channel.read(b) != -1);
    }

    private void resetCursors() {
        sPos = 0;
        ePos = 0;
        ePosIsAfterSPos = true;
    }

    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        return operateOnByteBufferWriteOut(maxBytesToWrite, channel::write);
    }

    public int writeToDatagramChannel(DatagramChannel channel, SocketAddress sockAddr, int maxBytesToWrite) throws IOException {
        return operateOnByteBufferWriteOut(maxBytesToWrite, b -> channel.send(buffer, sockAddr));
    }

    public int free() {
        return cap - used();
    }

    public int used() {
        if (ePosIsAfterSPos) {
            return ePos - sPos;
        } else {
            return ePos + cap - sPos;
        }
    }

    public int capacity() {
        return cap;
    }

    public byte[] getBytes() {
        int len = used();
        byte[] arr = new byte[len];
        if (len == 0)
            return arr;
        int lim = retrieveLimit();
        buffer.limit(sPos + lim).position(sPos);
        buffer.get(arr, 0, lim);
        if (ePosIsAfterSPos)
            return arr; // already reached limit
        int lim2 = retrieveLimit(0, true);
        if (lim2 == 0)
            return arr;
        //noinspection PointlessArithmeticExpression
        buffer.limit(0 + lim2).position(0);
        buffer.get(arr, lim, lim2);
        return arr;
    }

    @Override
    public String toString() {
        byte[] bytes = getBytes();
        return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
    }

    public void addHandler(RingBufferETHandler h) {
        if (operating) {
            handlerToRemove.remove(h);
            handlerToAdd.add(h);
        } else {
            handler.add(h);
        }
    }

    public void removeHandler(RingBufferETHandler h) {
        if (operating) {
            handlerToAdd.remove(h);
            handlerToRemove.add(h);
        } else {
            handler.remove(h);
        }
    }

    public void close() {
        closed = true;
    }

    private boolean cleaned = false;

    /**
     * release the direct memory<br>
     * PLEASE BE VERY CAREFUL
     */
    public void clean() {
        if (cleaned)
            return;
        cleaned = true;
        if (isDirect) {
            Utils.clean(buffer);
        }
    }

    // clear the buffer
    public void clear() {
        byte[] b = new byte[capacity()];
        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(b);

        // use a while loop because data may be read into buffer
        // on callback
        while (used() != 0) {
            writeTo(chnl);
            chnl.reset();
        }
    }

    interface WriteOutOp {
        void accept(ByteBuffer buffer) throws IOException;
    }

    int operateOnByteBufferWriteOut(int maxBytesToWrite, WriteOutOp op) throws IOException {
        if (closed)
            return 0; // handle nothing because it's closed

        operating = true;
        boolean triggerWritable = false;

        try { // only use try-finally here, we do not catch

            // is for triggering writable event
            final int freeSpace = free();
            boolean triggerWritablePre = freeSpace == 0 && !handler.isEmpty();

            int lim = retrieveLimit();
            if (lim == 0)
                return 0; // buffer is empty
            int realWrite = Math.min(lim, maxBytesToWrite);
            int newLimit = sPos + realWrite;
            buffer.limit(newLimit).position(sPos);

            // run the op
            op.accept(buffer);
            // and calculate writing bytes
            if (newLimit != buffer.limit()) {
                // limit of the buffer changed, which is illegal for writing
                throw new IllegalStateException("should only write out");
            }
            int write = (buffer.position() - sPos);
            sPos += write;

            triggerWritable = triggerWritablePre && write > 0;

            if (sPos == cap) {
                sPos = 0;
                ePosIsAfterSPos = true;
            }
            if (write == lim && write < maxBytesToWrite) {
                // maybe have more bytes to write
                lim = retrieveLimit();
                if (lim == 0) {
                    // buffer is empty now
                    resetCursors();
                    return write;
                }
                realWrite = Math.min(lim, maxBytesToWrite - write/* the bytes left to write */);
                newLimit = sPos + realWrite;
                buffer.limit(newLimit).position(sPos);

                // run the op
                op.accept(buffer);
                // and calculate writing bytes
                if (newLimit != buffer.limit()) {
                    // limit of the buffer changed, which is illegal for writing
                    throw new IllegalStateException("should only write out");
                }
                int write2 = (buffer.position() - sPos);
                sPos += write2;

                // this time, sPos will not reach cap
                // but let's check whether is empty for resetting cursor
                if (retrieveLimit() == 0) {
                    resetCursors();
                }
                return write + write2;
            } else {
                return write;
            }
        } finally { // do trigger here
            if (triggerWritable) {
                for (RingBufferETHandler aHandler : handler) {
                    aHandler.writableET();
                }
            }
            operating = false;
            handler.removeAll(handlerToRemove);
            handler.addAll(handlerToAdd);
        }
    }

    interface StoreInOp {
        boolean test(ByteBuffer buffer) throws IOException;
    }

    int operateOnByteBufferStoreIn(StoreInOp op) throws IOException {
        if (closed)
            return -1; // handle nothing because it's already closed

        operating = true;

        boolean triggerReadable = false;
        try { // only use try-finally here, we do not catch

            // is for triggering readable event
            final int usedSpace = used();
            boolean triggerReadablePre = usedSpace == 0 && !handler.isEmpty();

            int lim = storeLimit();
            if (lim == 0)
                return 0; // buffer is full
            int newLimit = ePos + lim;
            buffer.limit(newLimit).position(ePos);

            // run the op
            boolean succeeded = op.test(buffer);
            // calculate reading bytes
            if (newLimit != buffer.limit()) {
                // limit of the buffer changed, which is illegal
                throw new IllegalStateException("should only read in");
            }
            int read = buffer.position() - ePos;
            ePos += read;
            if (!succeeded)
                return -1; // some error occurred, maybe EOF

            triggerReadable = triggerReadablePre && read > 0;

            if (ePos == cap) {
                ePos = 0;
                ePosIsAfterSPos = false;
            }
            if (read == lim) {
                // maybe have more bytes to read
                lim = storeLimit();
                if (lim == 0)
                    return read; // buffer is full now
                newLimit = ePos + lim;
                buffer.limit(newLimit).position(ePos);

                // run the op
                succeeded = op.test(buffer);
                // calculate reading bytes
                if (newLimit != buffer.limit()) {
                    // limit of the buffer changed, which is illegal for reading
                    throw new IllegalStateException("should only read in");
                }
                int read2 = buffer.position() - ePos;
                ePos += read2;
                if (!succeeded) {
                    read2 = 0; // ignore error here, because the first read is ok
                }
                // this time, ePos will not reach cap
                return read + read2;
            } else {
                return read;
            }
        } finally { // do trigger here
            if (triggerReadable) {
                for (RingBufferETHandler aHandler : handler) {
                    aHandler.readableET();
                }
            }
            operating = false;
            handler.removeAll(handlerToRemove);
            handler.addAll(handlerToAdd);
        }
    }
}
