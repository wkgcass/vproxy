package io.vproxy.base.protocol;

import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.nio.ByteArrayChannel;

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
    public final NetEventLoop loop;
    public final ProtocolHandler<T> handler;

    // a field for user code to set data
    public T data;

    public ProtocolHandlerContext(String connectionId, Connection connection, NetEventLoop loop, ProtocolHandler<T> handler) {
        this.connectionId = connectionId;
        this.connection = connection;
        this.inBuffer = connection.getInBuffer();
        this.outBuffer = connection.getOutBuffer();
        this.loop = loop;
        this.handler = handler;
    }

    void doWrite() {
        assert Logger.lowLevelDebug("#" + hashCode() + " ::: doWrite with chnl=" + (chnl == null ? "null" : "used=" + chnl.used()) + ", bytesSeq.size=" + bytesSeq.size());

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
                assert Logger.lowLevelDebug("stored size = " + size);

                if (size == 0) {
                    break; // stored nothing, so just break
                    // in this case, the ET writable handler will not be called
                    // so break is safe
                }
            } // we should not use the `size` variable any more, so use a code block {} to prevent

            if (chnl != null && chnl.used() != 0) {
                assert Logger.lowLevelDebug("still have some bytes left, try to write again until write size equals 0");
                continue;
            }
            // otherwise,
            // this bytes array is already written
            // get another array
            byte[] bytes = bytesSeq.poll();
            if (bytes == null) {
                assert Logger.lowLevelDebug("nothing to write both in chnl and bytesSeq");
                chnl = null;
                break; // no more data to write
            }
            chnl = ByteArrayChannel.fromFull(bytes);
            // let it loop
        }
    }

    public void write(byte[] bytes) {
        if (connection.isClosed()) {
            Logger.error(LogType.IMPROPER_USE, "connection " + connection + " is already closed but still trying to write data");
            return;
        }
        if (bytes.length == 0)
            return; // do not write if the input array is empty
        assert Logger.lowLevelDebug("trying to write " + bytes.length + " in #" + hashCode());
        bytesSeq.add(bytes); // only record in this thread
        loop.getSelectorEventLoop().runOnLoop(this::doWrite); // run write in loop thread
    }

    @SuppressWarnings("unchecked")
    public void readable() {
        loop.getSelectorEventLoop().runOnLoop(() -> {
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
