package net.cassite.vproxy.util;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

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
public class RingBuffer {
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

    private RingBuffer(boolean isDirect, ByteBuffer buffer) {
        this.isDirect = isDirect;
        this.buffer = buffer;
        this.cap = buffer.capacity();
    }

    public static RingBuffer allocateDirect(int cap) {
        return new RingBuffer(true, ByteBuffer.allocateDirect(cap));
    }

    public static RingBuffer allocate(int cap) {
        return new RingBuffer(false, ByteBuffer.allocate(cap));
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
        // NOTE: ----------------
        // NOTE: the method has a copy: storeBytesFrom(Channel) for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------

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
            buffer.limit(ePos + lim).position(ePos);

            // calculate size for input byteBuffer
            int oldLimit = byteBuffer.limit();
            int read = byteBuffer.remaining();
            if (byteBuffer.remaining() > lim) {
                byteBuffer.limit(byteBuffer.position() + lim);
                read = lim;
            } // otherwise it's safe to write

            buffer.put(byteBuffer);

            ePos += read;

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

                byteBuffer.limit(oldLimit);
                int read2 = byteBuffer.remaining();
                if (read2 > lim) {
                    byteBuffer.limit(byteBuffer.position() + lim);
                    read2 = lim;
                }

                buffer.limit(ePos + lim).position(ePos);

                buffer.put(byteBuffer);

                ePos += read2;
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

        // NOTE: ----------------
        // NOTE: the method has a copy: storeBytesFrom(Channel) for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------
    }

    /**
     * @return may return -1 for EOF
     */
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        // NOTE: ----------------
        // NOTE: the method has a copy: storeBytesFrom(ByteBuffer) for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------

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
            buffer.limit(ePos + lim).position(ePos);
            int read = channel.read(buffer);
            if (read < 0)
                return read; // some error occurred, maybe EOF
            ePos += read;

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
                buffer.limit(ePos + lim).position(ePos);
                int read2 = channel.read(buffer);
                if (read2 < 0) {
                    read2 = 0; // ignore error here, because the first read is ok
                }
                ePos += read2;
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

        // NOTE: ----------------
        // NOTE: the method has a copy: storeBytesFrom(ByteBuffer) for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------
    }

    private void resetCursors() {
        sPos = 0;
        ePos = 0;
        ePosIsAfterSPos = true;
    }

    public int writeTo(WritableByteChannel channel) throws IOException {
        return writeTo(channel, Integer.MAX_VALUE);
    }

    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        // NOTE: ----------------
        // NOTE: the method has a copy: writeToDatagramChannel() for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------

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
            buffer.limit(sPos + realWrite).position(sPos);
            int write = channel.write(buffer);
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
                buffer.limit(sPos + realWrite).position(sPos);
                int write2 = channel.write(buffer);
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

        // NOTE: ----------------
        // NOTE: the method has a copy: writeToDatagramChannel() for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------
    }

    public int writeToDatagramChannel(DatagramChannel channel, SocketAddress sockAddr) throws IOException {
        return writeToDatagramChannel(channel, sockAddr, Integer.MAX_VALUE);
    }

    public int writeToDatagramChannel(DatagramChannel channel, SocketAddress sockAddr, int maxBytesToWrite) throws IOException {
        // NOTE: ----------------
        // NOTE: the method has a copy: writeTo() for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------

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
            buffer.limit(sPos + realWrite).position(sPos);
            int write = channel.send(buffer, sockAddr);
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
                buffer.limit(sPos + realWrite).position(sPos);
                int write2 = channel.send(buffer, sockAddr);
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

        // NOTE: ----------------
        // NOTE: the method has a copy: writeTo() for performance concern
        // NOTE: if anything changes here, do remember to change the copy
        // NOTE: ----------------
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
            try {
                writeTo(chnl);
            } catch (IOException e) {
                // it's memory operation
                // should not happen
            }
            chnl.reset();
        }
    }
}
