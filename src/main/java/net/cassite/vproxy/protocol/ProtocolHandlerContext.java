package net.cassite.vproxy.protocol;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ProtocolHandlerContext {
    private final ConcurrentLinkedQueue<byte[]> bytesSeq = new ConcurrentLinkedQueue<>();
    private ByteArrayChannel chnl = null; // the helper channel to write into out buffer
    // make inBuffer public for user code to read
    public final RingBuffer inBuffer;
    // make outBuffer private and handle the writings inside the lib
    private final RingBuffer outBuffer;
    // the loop that handles write process
    private final SelectorEventLoop loop;
    private final ProtocolHandler handler;

    ProtocolHandlerContext(Connection connection, SelectorEventLoop loop, ProtocolHandler handler) {
        this.inBuffer = connection.inBuffer;
        this.outBuffer = connection.outBuffer;
        this.loop = loop;
        this.handler = handler;
    }

    void doWrite() {
        // doWrite() should consider ET writable handler

        if (chnl != null && chnl.used() == 0)
            chnl = null; // remove channel if nothing to write
        if (chnl == null && bytesSeq.isEmpty())
            return; // nothing to write
        if (chnl == null) {
            byte[] bytes = bytesSeq.poll();
            assert bytes != null; // it only removes in one thread, so, no concurrency
            chnl = ByteArrayChannel.fromFull(bytes);
        }
        while (true) {
            {
                int size;
                try {
                    size = outBuffer.storeBytesFrom(chnl);
                } catch (IOException e) {
                    // will not happen
                    // because we are reading from memory
                    Logger.shouldNotHappen("should not get error when reading memory");
                    return; // just return for java syntax
                }
                // the chnl might be null because buffer ET writable handler called

                if (size == 0) {
                    break; // stored nothing, so just break
                    // in this case, the ET writable handler will not be called
                    // so break is safe
                }
            } // we should not use the `size` variable any more, so use a code block {} to prevent

            if (chnl != null && chnl.used() != 0) {
                break; // still have some bytes left, which means the outBuffer is full
                // this is ok even after ET writable handler
            }
            // otherwise,
            // this bytes array is already written
            // get another array
            byte[] bytes = bytesSeq.poll();
            if (bytes == null) {
                chnl = null;
                break; // no more data to write
            }
            chnl = ByteArrayChannel.fromFull(bytes);
            // let it loop
        }
    }

    public void write(byte[] bytes) {
        if (bytes.length == 0)
            return; // do not write if the input array is empty
        bytesSeq.add(bytes); // only record in this thread
        loop.runOnLoop(this::doWrite); // run write in loop thread
    }

    public void readable() {
        loop.runOnLoop(() -> {
            if (inBuffer.used() == 0)
                return; // do nothing if cannot read
            handler.readable(this);
        });
    }
}
