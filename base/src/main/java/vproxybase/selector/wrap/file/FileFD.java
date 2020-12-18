package vproxybase.selector.wrap.file;

import vfd.FD;
import vfd.IPPort;
import vfd.SocketFD;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.util.direct.DirectByteBuffer;
import vproxybase.util.direct.DirectMemoryUtils;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FileFD implements VirtualFD, SocketFD {
    private final SelectorEventLoop loop;
    private final FilePath filepath;
    private final List<OpenOption> openOptions;
    private final AsynchronousFileChannel asyncFile;
    private final long length;

    private boolean closed = false;

    private long readPosition = 0;
    private final DirectByteBuffer readBuffer;
    private final DirectByteBuffer writeBuffer;

    public FileFD(SelectorEventLoop loop, Path filePath, OpenOption... options) throws IOException {
        this.loop = loop;
        this.filepath = new FilePath(filePath.toAbsolutePath().toString());
        this.openOptions = Arrays.asList(options);
        this.asyncFile = AsynchronousFileChannel.open(filePath, options);
        try {
            this.length = asyncFile.size();
        } catch (IOException e) {
            try {
                asyncFile.close();
            } catch (IOException ignore) {
            }
            throw e;
        }

        this.readBuffer = DirectMemoryUtils.allocateDirectBuffer(8192);
        this.writeBuffer = DirectMemoryUtils.allocateDirectBuffer(8192);

        asyncRead();
    }

    public long length() {
        return length;
    }

    @Override
    public void connect(IPPort l4addr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnected() {
        return asyncFile.isOpen();
    }

    @Override
    public void shutdownOutput() {
        // not supported but do not raise exceptions
    }

    @Override
    public boolean finishConnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPPort getLocalAddress() {
        return filepath;
    }

    @Override
    public IPPort getRemoteAddress() {
        return filepath;
    }

    private boolean readable = false;
    private boolean writable = false;
    private boolean eof = false;
    private Throwable exception;

    private void checkClosed() throws IOException {
        if (closed) throw new IOException(this + " is already closed");
    }

    private void checkException() throws IOException {
        if (exception != null) {
            if (exception instanceof IOException) {
                throw (IOException) exception;
            } else {
                throw new IOException(exception);
            }
        }
    }

    private void setReadable() {
        readable = true;
        loop.selector.registerVirtualReadable(this);
    }

    private void setWritable() {
        writable = true;
        loop.selector.registerVirtualWritable(this);
    }

    private void cancelReadable() {
        readable = false;
        loop.selector.removeVirtualReadable(this);
    }

    private void cancelWritable() {
        writable = false;
        loop.selector.removeVirtualWritable(this);
    }

    private boolean isReading = false;

    private void asyncRead() {
        if (isReading) {
            return;
        }
        if (eof) {
            return;
        }
        isReading = true;
        cancelReadable();
        // NOTE: this method must be called after readBuffer is fully read by application code
        readBuffer.position(0).limit(readBuffer.capacity());
        asyncFile.read(readBuffer.realBuffer(), readPosition, null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                loop.runOnLoop(() -> {
                    int read = result;
                    if (read == -1) {
                        eof = true;
                    } else {
                        readPosition += read;
                        readBuffer.flip();
                    }
                    isReading = false;
                    if (readBuffer.limit() != readBuffer.position()) {
                        setReadable();
                    }
                });
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                loop.runOnLoop(() -> {
                    exception = exc;
                    isReading = false;
                    setReadable();
                    setWritable();
                });
            }
        });
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkClosed();
        checkException();
        if (eof) {
            return -1; // eof
        }
        if (dst.limit() == dst.position()) {
            return 0; // the dst is full
        }
        if (isReading) {
            cancelReadable();
            return 0;
        }
        if (readBuffer.limit() == readBuffer.position()) {
            asyncRead();
            return 0;
        }
        int read;
        if (dst.limit() - dst.position() >= readBuffer.limit() - readBuffer.position()) {
            // dst is capable of storing all data
            read = readBuffer.limit() - readBuffer.position();
            dst.put(readBuffer.realBuffer());
            // now the readBuffer should be empty, read again
            asyncRead();
        } else {
            // dst cannot store all data
            int originalLimit = readBuffer.limit();
            read = dst.limit() - dst.position();
            readBuffer.limit(readBuffer.position() + read);
            dst.put(readBuffer.realBuffer());
            // restore limit
            readBuffer.limit(originalLimit);
            // not fully read, wait for further user call
        }
        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkClosed();
        checkException();
        cancelWritable(); // TODO not supported yet
        return 0;
    }

    @Override
    public void onRegister() {
        if (readable) {
            setReadable();
        }
        if (writable) {
            setWritable();
        }
    }

    @Override
    public void onRemove() {
        // do nothing
    }

    @Override
    public void configureBlocking(boolean b) {
        if (b) throw new UnsupportedOperationException();
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) {
        // unsupported, but do not raise exceptions
    }

    @Override
    public FD real() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        asyncFile.close();
        readBuffer.clean();
        writeBuffer.clean();
    }

    @Override
    public String toString() {
        return "FileFD(" + filepath + " ::: " + openOptions + ")";
    }
}
