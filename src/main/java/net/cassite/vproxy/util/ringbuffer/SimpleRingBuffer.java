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

public class SimpleRingBuffer implements RingBuffer, ByteBufferRingBuffer {
    private final boolean isDirect;
    private /*may change after defragment*/ ByteBuffer buffer;
    private int ePos; // end pos
    private int sPos; // start pos
    private final int cap;
    private boolean ePosIsAfterSPos = true; // true then end is limit, otherwise start is limit
    private boolean closed = false;

    private boolean notFirstOperator = false;
    private boolean operating = false;
    private boolean operatingBuffer = false;
    private Set<RingBufferETHandler> handler = new HashSet<>();
    private Set<RingBufferETHandler> handlerToAdd = new HashSet<>();
    private Set<RingBufferETHandler> handlerToRemove = new HashSet<>();

    public static SimpleRingBuffer allocateDirect(int cap) {
        return new SimpleRingBuffer(true, ByteBuffer.allocateDirect(cap));
    }

    public static SimpleRingBuffer allocate(int cap) {
        return new SimpleRingBuffer(false, ByteBuffer.allocate(cap));
    }

    private SimpleRingBuffer(boolean isDirect, ByteBuffer buffer) {
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
        assert Logger.lowLevelNetDebug("reset cursors");
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

    @Override
    public Set<RingBufferETHandler> getHandlers() {
        return new HashSet<>(handler);
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

    private boolean isFirstOperate() {
        assert Logger.lowLevelNetDebug("thread " + Thread.currentThread() + " is operating");
        boolean firstOperator = !notFirstOperator;
        if (firstOperator) {
            notFirstOperator = true;
        }
        operating = true;
        return firstOperator;
    }

    private void resetFirst(boolean firstOperator) {
        if (!firstOperator)
            return;
        operating = false;
        notFirstOperator = false;

        handler.removeAll(handlerToRemove);
        handler.addAll(handlerToAdd);
    }

    public int operateOnByteBufferWriteOut(int maxBytesToWrite, ByteBufferRingBuffer.WriteOutOp op) throws IOException {
        if (closed)
            return 0; // handle nothing because it's closed
        if (operatingBuffer) {
            throw new IllegalStateException("this buffer is operating");
        }

        boolean firstOperator = isFirstOperate();
        operatingBuffer = true;

        boolean triggerWritable = false;
        int bytesBeforeOperating = used();

        assert Logger.lowLevelNetDebug("before operate write out, sPos=" + sPos);

        try { // only use try-finally here, we do not catch

            // is for triggering writable event
            boolean triggerWritablePre = free() == 0;

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
                assert Logger.lowLevelDebug("newLimit=" + newLimit + ", buffer.limit()=" + buffer.limit());
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
                    assert Logger.lowLevelDebug("newLimit=" + newLimit + ", buffer.limit()=" + buffer.limit());
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
            assert Logger.lowLevelNetDebug("after operate write out, sPos=" + sPos);

            // was > 0 and now nothing
            // which means all data had been flushed out
            boolean flushAwareCondition = bytesBeforeOperating > 0 && used() == 0;

            operatingBuffer = false;
            if (triggerWritable || flushAwareCondition /*precondition, would check whether the handler is aware of*/) {
                assert Logger.lowLevelNetDebug("trigger writable for " + handler.size() + " times");
                for (RingBufferETHandler aHandler : handler) {
                    // because the preconditions are checked, so
                    // if not triggerWritable, then flushAwareCondition is definitely true
                    // no need to check for it again here
                    if (triggerWritable || aHandler.flushAware()) {
                        aHandler.writableET();
                    }
                }
            }
            resetFirst(firstOperator);
        }
    }

    public int operateOnByteBufferStoreIn(ByteBufferRingBuffer.StoreInOp op) throws IOException {
        if (closed)
            return -1; // handle nothing because it's already closed
        if (operatingBuffer) {
            throw new IllegalStateException("this buffer is operating");
        }

        boolean firstOperator = isFirstOperate();
        operatingBuffer = true;

        boolean triggerReadable = false;

        assert Logger.lowLevelNetDebug("before operate store in, ePos=" + ePos);

        try { // only use try-finally here, we do not catch

            // is for triggering readable event
            boolean triggerReadablePre = used() == 0;

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
                assert Logger.lowLevelDebug("newLimit=" + newLimit + ", buffer.limit()=" + buffer.limit());
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
                    assert Logger.lowLevelDebug("newLimit=" + newLimit + ", buffer.limit()=" + buffer.limit());
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
            assert Logger.lowLevelNetDebug("after operate store in, ePos=" + ePos);

            operatingBuffer = false;
            if (triggerReadable) {
                assert Logger.lowLevelNetDebug("trigger readable for " + handler.size() + " times");
                for (RingBufferETHandler aHandler : handler) {
                    aHandler.readableET();
                }
            }
            resetFirst(firstOperator);
        }
    }

    @Override
    public boolean canDefragment() {
        return sPos != 0;
    }

    @Override
    public void defragment() {
        if (operating)
            throw new IllegalStateException("cannot perform defragment when it's operating");

        if (sPos == 0)
            return; // no need to defragment if sPos is already 0
        // we make the code simple:
        // create a new buffer with exactly the same capacity
        // and store data into the new buffer
        //
        // then we make a swap
        ByteBuffer newBuffer;
        if (isDirect) {
            newBuffer = ByteBuffer.allocateDirect(cap);
        } else {
            newBuffer = ByteBuffer.allocate(cap);
        }

        if (ePosIsAfterSPos) {
            buffer.limit(ePos).position(sPos); // sPos, ePos, cap
        } else {
            buffer.limit(cap).position(sPos); // ePos, sPos, cap
        }
        newBuffer.put(buffer);
        if (!ePosIsAfterSPos) {
            // still have some bytes
            buffer.limit(ePos).position(0);
            newBuffer.put(buffer);
        }

        if (isDirect) {
            Utils.clean(buffer); // clean the old buffer
        }

        sPos = 0;
        ePos = newBuffer.position();
        ePosIsAfterSPos = true;
        buffer = newBuffer;
    }
}
