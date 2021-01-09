package vproxybase.selector.wrap.blocking;

import vfd.AbstractDatagramFD;
import vfd.FD;
import vfd.SockAddr;
import vfd.type.FDCloseReq;
import vfd.type.FDCloseReturn;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.selector.wrap.WrappedSelector;
import vproxybase.util.Lock;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.VProxyThread;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class BlockingDatagramFD<ADDR extends SockAddr> implements AbstractDatagramFD<ADDR>, VirtualFD {
    private final AbstractDatagramFD<ADDR> fd;
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

    public BlockingDatagramFD(AbstractDatagramFD<ADDR> fd, SelectorEventLoop loop,
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

        readThread = new VProxyThread(this::threadRead, "blocking-read-" + fd.toString());
        readThread.start();
        writeThread = new VProxyThread(this::threadWrite, "blocking-write-" + fd.toString());
        writeThread.start();
    }

    private void setReadable() {
        loop.runOnLoop(() -> selector.registerVirtualReadable(this));
    }

    private void cancelReadable() {
        loop.runOnLoop(() -> selector.removeVirtualReadable(this));
    }

    private void setWritable() {
        selector.registerVirtualWritable(this);
    }

    @Override
    public void connect(ADDR addr) throws IOException {
        fd.connect(addr);
    }

    @Override
    public void bind(ADDR addr) throws IOException {
        fd.bind(addr);
    }

    @Override
    public int send(ByteBuffer buf, ADDR remote) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("not supported for now");
    }

    @Override
    public ADDR receive(ByteBuffer buf) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("not supported for now");
    }

    @Override
    public ADDR getLocalAddress() throws IOException {
        return fd.getLocalAddress();
    }

    @Override
    public ADDR getRemoteAddress() throws IOException {
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
                cancelReadable();
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
        //noinspection unused
        try (var x = readQLock.lock()) {
            if (!readQueue.isEmpty()) {
                setReadable();
            }
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
    public FDCloseReturn close(FDCloseReq req) throws IOException {
        if (isClosed) {
            fd.close(); // still proxy the call to fd
            return FDCloseReturn.nothing(req);
        }
        isClosed = true;
        fd.close();
        readThread.interrupt();
        writeThread.interrupt();
        return FDCloseReturn.nothing(req);
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
                setReadable();
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
            setReadable();
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
