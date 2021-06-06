package vproxy.base.selector.wrap.file;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.AbstractBaseVirtualSocketFD;
import vproxy.base.selector.wrap.VirtualFD;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.direct.DirectByteBuffer;
import vproxy.base.util.direct.DirectMemoryUtils;
import vproxy.vfd.IPPort;
import vproxy.vfd.SocketFD;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FileFD extends AbstractBaseVirtualSocketFD implements VirtualFD, SocketFD {
    private final List<OpenOption> openOptions;
    private String filepath;
    private AsynchronousFileChannel asyncFile;
    private long length;

    private long readPosition = 0;
    private final DirectByteBuffer readBuffer;
    private final DirectByteBuffer writeBuffer;

    public FileFD(SelectorEventLoop loop, OpenOption... options) {
        super(false, null, null);
        this.openOptions = Arrays.asList(options);

        this.readBuffer = DirectMemoryUtils.allocateDirectBuffer(8192);
        this.writeBuffer = DirectMemoryUtils.allocateDirectBuffer(8192);

        loopAware(loop);
    }

    public long length() {
        return length;
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        super.connect(l4addr);

        if (!(l4addr instanceof FilePath)) {
            throw new IOException("unsupported address type: " + l4addr + " is not FilePath");
        }

        Path path = Path.of(((FilePath) l4addr).filepath);
        this.filepath = path.toAbsolutePath().toString();
        OpenOption[] options = new OpenOption[openOptions.size()];
        openOptions.toArray(options);
        this.asyncFile = AsynchronousFileChannel.open(path, options);
        try {
            this.length = asyncFile.size();
        } catch (IOException e) {
            try {
                asyncFile.close();
            } catch (IOException ignore) {
            }
            throw e;
        }
        alertConnected(l4addr);
        // start reading
        asyncRead();
    }

    private boolean isReading = false;

    private void asyncRead() {
        if (isReading) {
            return;
        }
        if (isEof()) {
            return;
        }
        isReading = true;
        cancelReadable();
        // NOTE: this method must be called after readBuffer is fully read by application code
        readBuffer.position(0).limit(readBuffer.capacity());
        asyncFile.read(readBuffer.realBuffer(), readPosition, null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                getLoop().runOnLoop(() -> {
                    int read = result;
                    if (read == -1) {
                        setEof();
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
                getLoop().runOnLoop(() -> {
                    raiseError(exc);
                    isReading = false;
                });
            }
        });
    }

    @Override
    protected boolean noDataToRead() {
        return isReading || (readBuffer.limit() - readBuffer.position() == 0);
    }

    @Override
    protected int doRead(ByteBuffer dst) {
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
    protected boolean noSpaceToWrite() {
        return true; // TODO writing is not supported yet
    }

    @Override
    protected int doWrite(ByteBuffer src) {
        return 0; // TODO writing is not supported yet
    }

    @Override
    protected void doClose(boolean reset) {
        try {
            asyncFile.close();
        } catch (IOException e) {
            Logger.error(LogType.FILE_ERROR, "closing asyncFile " + asyncFile + " failed", e);
        }
        readBuffer.clean();
        writeBuffer.clean();
    }

    @Override
    protected String formatToString() {
        return "FileFD(" + filepath + " ::: " + openOptions + ")";
    }
}
