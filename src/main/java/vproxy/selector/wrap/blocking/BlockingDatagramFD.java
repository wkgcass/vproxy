package vproxy.selector.wrap.blocking;

import vfd.DatagramFD;
import vfd.FD;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.util.Lock;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class BlockingDatagramFD implements DatagramFD, VirtualFD {
    private final DatagramFD fd;
    private final SelectorEventLoop loop;
    private final WrappedSelector selector;
    private final int readBufSize;
    private final int writeQByteLimit;
    private final int readBufPacketLimit;
    private volatile boolean isClosed = false;


    // all queues add to the tail, read from the head

    private final Lock readQLock = Lock.create();
    private final LinkedList<ByteBuffer> readQueue = new LinkedList<>();
    private final Thread readThread;
    private IOException lastReadException = null;
    private volatile boolean isReading = false;

    private final Lock writeQLock = Lock.create();
    private final LinkedList<ByteBuffer> writeQueue = new LinkedList<>();
    private final Thread writeThread;
    private int currentWriteQueueBytes = 0;
    private IOException lastWriteException = null;
    private volatile boolean isWriting = false;

    public BlockingDatagramFD(DatagramFD fd, SelectorEventLoop loop,
                              int readBufSize, int writeQByteLimit, int readBufPacketLimit) {
        if (!fd.isOpen()) {
            throw new IllegalArgumentException("trying to handle a closed channel: " + fd);
        }

        this.fd = fd;
        this.loop = loop;
        this.selector = loop.selector;
        this.readBufSize = readBufSize;
        this.writeQByteLimit = writeQByteLimit;
        this.readBufPacketLimit = readBufPacketLimit;

        readThread = new Thread(this::threadRead, "blocking-read-" + fd.toString());
        readThread.start();
        writeThread = new Thread(this::threadWrite, "blocking-write-" + fd.toString());
        writeThread.start();
    }

    private boolean isReadable() {
        //noinspection unused
        try (var x = readQLock.lock()) {
            return !readQueue.isEmpty();
        }
    }

    private void setReadable() {
        selector.registerVirtualReadable(this);
    }

    private void cancelReadable() {
        selector.removeVirtualReadable(this);
    }

    private void setWritable() {
        selector.registerVirtualWritable(this);
    }

    @Override
    public void connect(InetSocketAddress l4addr) throws IOException {
        fd.connect(l4addr);
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        fd.bind(l4addr);
    }

    @Override
    public int send(ByteBuffer buf, InetSocketAddress remote) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("not supported for now");
    }

    @Override
    public SocketAddress receive(ByteBuffer buf) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("not supported for now");
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return fd.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return fd.getRemoteAddress();
    }

    // write only one packet to the dst in one call
    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (lastReadException != null) {
            var e = lastReadException;
            lastReadException = null;
            resumeBlockingRead();
            throw e;
        }

        int dstLen = dst.limit() - dst.position();
        if (dstLen == 0) {
            return 0;
        }
        int dstPosBackup = dst.position();
        //noinspection unused
        try (var x = readQLock.lock()) {
            if (readQueue.isEmpty()) {
                return 0;
            }
            ByteBuffer src = readQueue.getFirst();
            int srcLen = src.limit() - src.position();
            if (srcLen <= dstLen) {
                // the whole packet can be put into the dst
                readQueue.removeFirst();
                resumeBlockingRead();
                dst.put(src);
            }
            if (readQueue.isEmpty()) {
                cancelReadable();
            }
        }
        return dst.position() - dstPosBackup;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (lastWriteException != null) {
            var e = lastWriteException;
            lastWriteException = null;
            resumeBlockingWrite();
            throw e;
        }

        int srcLen = src.limit() - src.position();
        if (srcLen == 0) {
            return 0;
        }
        //noinspection unused
        try (var x = writeQLock.lock()) {
            // check len limit
            if (currentWriteQueueBytes + srcLen > writeQByteLimit) { // cannot write
                Logger.warn(LogType.BUFFER_INSUFFICIENT, "cannot store bytes into the write queue: current: " + currentWriteQueueBytes + ", input: " + srcLen + ", limit: " + writeQByteLimit);
                return 0;
            }
            var copy = ByteBuffer.allocate(srcLen);
            copy.put(src);
            copy.flip();
            writeQueue.add(copy);
            currentWriteQueueBytes += srcLen;
            if (currentWriteQueueBytes > writeQByteLimit * 0.8) {
                Logger.warn(LogType.BUFFER_INSUFFICIENT, "the write queue consumes more than 80% of the limit: current: " + currentWriteQueueBytes + ", limit: " + writeQByteLimit);
            }
        }
        resumeBlockingWrite();
        return srcLen;
    }

    @Override
    public void onRegister() {
        resumeBlockingThreads();
        if (isReadable()) {
            setReadable();
        }
        setWritable();
    }

    @Override
    public void onRemove() {
        pauseBlockingThreads();
    }

    @Override
    public void configureBlocking(boolean b) {
        // ignore
    }

    private void resumeBlockingThreads() {
        resumeBlockingRead();
        resumeBlockingWrite();
    }

    private void pauseBlockingThreads() {
        pauseBlockingRead();
        pauseBlockingWrite();
    }

    private void resumeBlockingRead() {
        if (isReading)
            return;
        isReading = true;
        readThread.interrupt();
    }

    private void pauseBlockingRead() {
        isReading = false;
    }

    private void resumeBlockingWrite() {
        if (isWriting)
            return;
        isWriting = true;
        writeThread.interrupt();
    }

    private void pauseBlockingWrite() {
        isWriting = false;
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        fd.setOption(name, value);
    }

    @Override
    public FD real() {
        return fd;
    }

    @Override
    public boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (isClosed) {
            fd.close(); // still proxy the call to fd
            return;
        }
        isClosed = true;
        fd.close();
        readThread.interrupt();
        writeThread.interrupt();
    }

    private void threadRead() {
        //noinspection ConditionalBreakInInfiniteLoop
        while (true) {
            if (isClosed) {
                break;
            }
            if (!isReading) {
                try {
                    Thread.sleep(24 * 3600 * 1000);
                } catch (InterruptedException ignore) {
                }
                continue;
            }

            doRead();
        }
    }

    private void doRead() {
        //noinspection unused
        try (var x = readQLock.lock()) {
            if (readQueue.size() >= readBufPacketLimit) {
                assert Logger.lowLevelDebug("cannot store packets into the readQueue: size = " + readQueue.size() + ", limit = " + readBufPacketLimit);
                loop.runOnLoop(this::setReadable);
                isReading = false;
                return;
            }
        }

        var buf = ByteBuffer.allocate(readBufSize);
        try {
            fd.read(buf);
        } catch (IOException e) {
            lastReadException = e;
            isReading = false;
            return;
        }
        buf.flip();
        //noinspection unused
        try (var x = readQLock.lock()) {
            readQueue.addLast(buf);
            loop.runOnLoop(this::setReadable);
        }
    }

    private void threadWrite() {
        //noinspection ConditionalBreakInInfiniteLoop
        while (true) {
            if (isClosed) {
                break;
            }
            if (!isWriting) {
                try {
                    Thread.sleep(24 * 3600 * 1000);
                } catch (InterruptedException ignore) {
                }
                continue;
            }

            doWrite();
        }
    }

    private void doWrite() {
        ByteBuffer buf;
        //noinspection unused
        try (var x = writeQLock.lock()) {
            if (writeQueue.isEmpty()) {
                isWriting = false;
                return;
            }
            buf = writeQueue.getFirst();
        }

        int bufLen = buf.limit() - buf.position();
        int n;
        try {
            n = fd.write(buf);
        } catch (IOException e) {
            lastWriteException = e;
            isWriting = false;
            return;
        }

        if (n <= 0) { // nothing wrote
            Logger.shouldNotHappen("we expect a blocking call to write, but return value is " + n + " on fd " + fd);
            return; // run the next doWrite()
        }
        if (n != bufLen) {
            Logger.shouldNotHappen("the datagram fd is writing partial packet: " + fd + ", n = " + n + ", bufLen = " + bufLen);
        }

        //noinspection unused
        try (var x = writeQLock.lock()) {
            writeQueue.removeFirst();
            currentWriteQueueBytes -= n;
        }
    }

    @Override
    public String toString() {
        return "BlockingDatagramFD{" +
            "fd=" + fd +
            '}';
    }
}
