package io.vproxy.base.util.file;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.promise.Promise;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class MappedByteBufferLogger {
    private final String prefix;
    private final String suffix;
    private final long size;
    private final long preNewFileThreshold;
    private int fileNameIndex = 0;
    private MappedByteBuffer current;
    private volatile Tuple<Promise<Void>, Callback<Void, Throwable>> futureTuple = null; // null means no background task is running
    private volatile MappedByteBuffer newBuffer;
    private volatile IOException fileCreationTaskFailureException = null;

    public MappedByteBufferLogger(String location, String prefix, String suffix, long size, long preNewFileThreshold) throws IOException {
        assert size > preNewFileThreshold;
        assert size > 0;
        assert preNewFileThreshold >= 0;

        if (!new File(location).isDirectory()) {
            throw new IOException(location + " is not a directory");
        }

        this.prefix = new File(location).getCanonicalPath() + File.separator + prefix;
        this.suffix = suffix;
        this.size = size;
        this.preNewFileThreshold = preNewFileThreshold;

        newFile();
        handleTaskResultAndReplaceBuffers();
    }

    private String nextFileNameIndex() {
        var n = (fileNameIndex++);
        var s = "" + n;
        if (s.length() < 4) {
            s = "0".repeat(4 - s.length()) + s;
        }
        return s;
    }

    private void newFile() throws IOException {
        var file = new File(prefix + nextFileNameIndex() + suffix);
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();

        try (var rfile = new RandomAccessFile(file, "rw")) {
            rfile.setLength(size);
        }

        try (var channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            newBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
        }
    }

    private Promise<Void> prepareNewFile() {
        if (futureTuple != null) { // task is running
            return futureTuple._1; // no need to synchronize. if promise changed to null, it means the task is done
        }
        if (newBuffer != null) { // new buffer is already created
            return null;
        }
        if (fileCreationTaskFailureException != null) { // task failed
            return null;
        }
        // need to start the task
        return doStartNewFileTask();
    }

    private Promise<Void> doStartNewFileTask() {
        var tuple = Promise.<Void>todo();
        futureTuple = tuple;
        new Thread(() -> {
            try {
                newFile();
            } catch (IOException e) {
                fileCreationTaskFailureException = e;
            } finally {
                futureTuple = null;
            }
            if (fileCreationTaskFailureException != null) {
                tuple._2.failed(fileCreationTaskFailureException);
            } else {
                tuple._2.succeeded();
            }
        }).start();
        return tuple._1;
    }

    private void blockOnNewFileTask() throws IOException {
        var tuple = this.futureTuple;
        if (tuple != null) {
            waitForTask(tuple);
            return;
        }
        if (newBuffer != null || fileCreationTaskFailureException != null) {
            handleTaskResultAndReplaceBuffers();
            return;
        }
        // task is not started yet, directly run on this thread
        try {
            newFile();
        } catch (IOException e) {
            close();
            Logger.error(LogType.FILE_ERROR, "failed creating new file for " + prefix + "..." + suffix);
            throw e;
        }
        handleTaskResultAndReplaceBuffers();
    }

    private void waitForTask(Tuple<Promise<Void>, Callback<Void, Throwable>> tuple) throws IOException {
        try {
            tuple._1.block();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
        handleTaskResultAndReplaceBuffers();
    }

    private void handleTaskResultAndReplaceBuffers() throws IOException {
        if (fileCreationTaskFailureException != null) {
            var e = fileCreationTaskFailureException;
            fileCreationTaskFailureException = null;
            throw e;
        }
        if (current != null) {
            var current = this.current;
            new Thread(current::force).start();
        }
        current = newBuffer;
        newBuffer = null;
    }

    private boolean newFileTaskDone() {
        return futureTuple == null && (newBuffer != null || fileCreationTaskFailureException != null);
    }

    public WriteOrDropResult writeOrDrop(String s) throws IOException {
        return writeOrDrop(ByteArray.from(s));
    }

    public WriteOrDropResult writeOrDrop(ByteArray data) throws IOException {
        if (current == null)
            throw new IllegalStateException("closed");

        if (data.length() > 2 * size) {
            // too large even for 2 background buffers
            // so discard it
            return new WriteOrDropResult(WriteOrDropResultType.DROP_TOO_LARGE, null);
        }
        if (data.length() > size) {
            if (data.length() > size + current.limit() - current.position()) {
                // too large for a single background buffer,
                // need to create 2 background buffers.
                // since we only allow 1 background buffer,
                // so must create one, use it,
                // then create another one
                if (newFileTaskDone()) {
                    handleTaskResultAndReplaceBuffers();
                }
                var promise = prepareNewFile();
                return new WriteOrDropResult(WriteOrDropResultType.DROP_PENDING, promise);
            } else {
                // ok for a single background buffer
                // so normal check ...
                if (!newFileTaskDone()) {
                    var promise = prepareNewFile();
                    return new WriteOrDropResult(WriteOrDropResultType.DROP_PENDING, promise);
                }
            }
        } else {
            if (current.limit() - current.position() < data.length()) {
                // cannot fill, need a new buffer
                if (!newFileTaskDone()) {
                    var promise = prepareNewFile();
                    return new WriteOrDropResult(WriteOrDropResultType.DROP_PENDING, promise);
                }
            }
        }
        // can write
        writeAndBlock(data);
        return new WriteOrDropResult(WriteOrDropResultType.WRITTEN, null);
    }

    public record WriteOrDropResult(
        WriteOrDropResultType type,
        Promise<Void> writablePromise
    ) {
    }

    public enum WriteOrDropResultType {
        WRITTEN,
        DROP_PENDING,
        DROP_TOO_LARGE,
        ;

        WriteOrDropResultType() {
        }

        public boolean isDropped() {
            return this != WRITTEN;
        }

        public boolean isWritten() {
            return this == WRITTEN;
        }
    }

    @Blocking
    public void writeAndBlock(String s) throws IOException {
        writeAndBlock(ByteArray.from(s));
    }

    @Blocking
    public void writeAndBlock(ByteArray data) throws IOException {
        if (current == null)
            throw new IllegalStateException("closed");

        if (data.length() > size) {
            var split = current.limit() - current.position();
            if (split <= 0) {
                split = (int) size;
            }
            var left = data.sub(0, split);
            var right = data.sub(split, data.length() - split);

            writeAndBlock(left);
            writeAndBlock(right);
            return;
        }

        if (current.limit() - current.position() < data.length()) {
            // need a new file
            blockOnNewFileTask();
            writeAndBlock(data);
            return;
        }

        current.put(data.toJavaArray());

        if (current.limit() - current.position() <= preNewFileThreshold) {
            prepareNewFile();
        }
    }

    @Blocking
    public void close() {
        close(false);
    }

    public void closeAndFlushOnNewThread() {
        close(true);
    }

    private void close(boolean flushOnNewThread) {
        if (current != null) {
            if (flushOnNewThread) {
                var current = this.current;
                new Thread(current::force).start();
            } else {
                current.force();
            }
        }
        current = null;
        newBuffer = null;
        fileCreationTaskFailureException = null;
    }
}
