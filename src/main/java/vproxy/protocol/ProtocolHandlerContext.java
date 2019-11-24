package vproxy.protocol;

import vproxy.connection.Connection;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.RingBuffer;
import vproxy.util.nio.ByteArrayChannel;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ProtocolHandlerContext<T> {
    private final ConcurrentLinkedQueue<byte[]> bytesSeq = new ConcurrentLinkedQueue<>();
    private ByteArrayChannel chnl = null; // the helper channel to write into out buffer
    public final String connectionId;
    public final Connection connection;
    // make inBuffer public for user code to read
    public final RingBuffer inBuffer;
    // make outBuffer private and handle the writings inside the lib
    private final RingBuffer outBuffer;
    // the loop that handles write process
    public final SelectorEventLoop loop;
    private final ProtocolHandler handler;

    // a field for user code to set data
    public T data;

    public ProtocolHandlerContext(String connectionId, Connection connection, SelectorEventLoop loop, ProtocolHandler handler) {
        this.connectionId = connectionId;
        this.connection = connection;
        this.inBuffer = connection.getInBuffer();
        this.outBuffer = connection.getOutBuffer();
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
                int size = outBuffer.storeBytesFrom(chnl);
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

    @SuppressWarnings("unchecked")
    public void readable() {
        loop.runOnLoop(() -> {
            if (inBuffer.used() == 0)
                return; // do nothing if cannot read
            handler.readable(this);
        });
    }

    @Override
    public String toString() {
        return "ProtocolHandlerContext{" +
            "connectionId='" + connectionId + '\'' +
            ", connection=" + connection +
            ", loop=" + loop +
            ", handler=" + handler +
            ", data=" + data +
            '}';
    }
}
