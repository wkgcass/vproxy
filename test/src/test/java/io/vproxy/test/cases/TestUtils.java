package io.vproxy.test.cases;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.bytearray.RandomAccessFileByteArray;
import io.vproxy.base.util.file.MappedByteBufferLogger;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.objectpool.ConcurrentObjectPool;
import io.vproxy.base.util.ringbuffer.SimpleRingBuffer;
import io.vproxy.base.util.ringbuffer.SimpleRingBufferReaderCommitter;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.commons.util.IOUtils;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TestUtils {
    @After
    public void tearDown() throws Exception {
        if (tempPath != null) {
            IOUtils.deleteDirectory(tempPath.toFile());
        }
    }

    private void committerIdleCheck(SimpleRingBufferReaderCommitter committer) {
        assertTrue(committer.isIdle());
        assertTrue(committer.check());
        assertEquals("SimpleRingBufferReaderCommitter[]", committer.toString());
    }

    @Test
    public void ringBufferReaderCommitter() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            assertEquals(0, committer.read().length);
            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abc")));
            var segs = committer.read();
            assertEquals(1, segs.length);
            assertEquals(3, segs[0].byteSize());
            assertArrayEquals("abc".getBytes(), segs[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=3, len=3}]", committer.toString());

            committer.commit(segs[0]);
            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    @Test
    public void ringBufferReaderCommitterEBeforeS() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abcd")));
            var segs1 = committer.read();
            assertEquals(1, segs1.length);
            assertEquals(4, segs1[0].byteSize());
            assertArrayEquals("abcd".getBytes(), segs1[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("efg")));
            var segs2 = committer.read();
            assertEquals(1, segs2.length);
            assertEquals(3, segs2[0].byteSize());
            assertArrayEquals("efg".getBytes(), segs2[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}, {sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs1[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("hijkl")));
            var segs3 = committer.read();
            assertEquals(2, segs3.length);
            assertEquals(3, segs3[0].byteSize());
            assertEquals(2, segs3[1].byteSize());
            assertArrayEquals("hij".getBytes(), segs3[0].toArray(ValueLayout.JAVA_BYTE));
            assertArrayEquals("kl".getBytes(), segs3[1].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            committer.commit(segs2[0]);
            committer.commit(segs3[0]);
            committer.commit(segs3[1]);

            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    @Test
    public void ringBufferReaderCommitterUnorderedCommit() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abcd")));
            var segs1 = committer.read();
            assertEquals(1, segs1.length);
            assertEquals(4, segs1[0].byteSize());
            assertArrayEquals("abcd".getBytes(), segs1[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("efg")));
            var segs2 = committer.read();
            assertEquals(1, segs2.length);
            assertEquals(3, segs2[0].byteSize());
            assertArrayEquals("efg".getBytes(), segs2[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}, {sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs1[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("hijkl")));
            var segs3 = committer.read();
            assertEquals(2, segs3.length);
            assertEquals(3, segs3[0].byteSize());
            assertEquals(2, segs3[1].byteSize());
            assertArrayEquals("hij".getBytes(), segs3[0].toArray(ValueLayout.JAVA_BYTE));
            assertArrayEquals("kl".getBytes(), segs3[1].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            int n = rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("mn[...]")));
            assertEquals(2, n);

            var segs4 = committer.read();
            assertEquals(1, segs4.length);
            assertEquals(2, segs4[0].byteSize());
            assertArrayEquals("mn".getBytes(), segs4[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs3[1]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs2[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=7, ePos=0, len=3}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs3[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs4[0]);
            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    @Test
    public void ringBufferReaderCommitterUnorderedCommit2() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abcd")));
            var segs1 = committer.read();
            assertEquals(1, segs1.length);
            assertEquals(4, segs1[0].byteSize());
            assertArrayEquals("abcd".getBytes(), segs1[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("efg")));
            var segs2 = committer.read();
            assertEquals(1, segs2.length);
            assertEquals(3, segs2[0].byteSize());
            assertArrayEquals("efg".getBytes(), segs2[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}, {sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs1[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("hijkl")));
            var segs3 = committer.read();
            assertEquals(2, segs3.length);
            assertEquals(3, segs3[0].byteSize());
            assertEquals(2, segs3[1].byteSize());
            assertArrayEquals("hij".getBytes(), segs3[0].toArray(ValueLayout.JAVA_BYTE));
            assertArrayEquals("kl".getBytes(), segs3[1].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            int n = rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("mn[...]")));
            assertEquals(2, n);

            var segs4 = committer.read();
            assertEquals(1, segs4.length);
            assertEquals(2, segs4[0].byteSize());
            assertArrayEquals("mn".getBytes(), segs4[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs4[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            committer.commit(segs3[1]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}]", committer.toString());

            committer.commit(segs3[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs2[0]);
            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    private Path tempPath; // will teardown
    private String prefix;

    private String readTempContent(String sequence) throws IOException {
        var bytes = Files.readAllBytes(Path.of(tempPath.toString(), prefix + sequence + ".log"));
        if (bytes[bytes.length - 1] != 0) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return MemorySegment.ofArray(bytes).getString(0);
    }

    private void assertFilesCnt(int cnt) {
        var files = tempPath.toFile().listFiles();
        assert files != null;
        assertEquals(cnt, files.length);

        var set = Arrays.stream(files).map(File::getName).collect(Collectors.toSet());
        for (int i = 0; i < cnt; ++i) {
            var s = "" + i;
            if (s.length() < 4) {
                s = "0".repeat(4 - s.length()) + s;
            }
            assertTrue(set.contains(prefix + s + ".log"));
        }
    }

    private String getCurrentTimestampForFileName() {
        return Utils.formatTimestampForFileName(System.currentTimeMillis());
    }

    @Test
    public void simpleMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-simpleMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 1048576, 65536);
        logger.writeAndBlock("hello world\n");

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(1);
        var content = readTempContent("0000");
        assertEquals("hello world\n", content);
    }

    @Test
    public void simpleMappedByteBufferLoggerWriteOrDrop() throws Throwable {
        tempPath = Files.createTempDirectory("test-simpleMappedByteBufferLoggerWriteOrDrop");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 1048576, 65536);
        var res = logger.writeOrDrop("hello world\n");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());
        assertNull(res.writablePromise());

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(1);
        var content = readTempContent("0000");
        assertEquals("hello world\n", content);
    }

    @Test
    public void splitMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-splitMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 0);
        logger.writeAndBlock("hello world\n");

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(2);

        var content = readTempContent("0000");
        assertEquals("hello wo", content);

        content = readTempContent("0001");
        assertEquals("rld\n", content);
    }

    @Test
    public void splitMappedByteBufferLoggerWriteOrDrop() throws Throwable {
        tempPath = Files.createTempDirectory("test-splitMappedByteBufferLoggerWriteOrDrop");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 0);
        var res = logger.writeOrDrop("hello world\n");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.DROP_PENDING, res.type());
        res.writablePromise().block();
        res = logger.writeOrDrop("hello world\n");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(2);

        var content = readTempContent("0000");
        assertEquals("hello wo", content);

        content = readTempContent("0001");
        assertEquals("rld\n", content);
    }

    @Test
    public void createMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-createMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 1);
        logger.writeAndBlock("1234567");
        logger.writeAndBlock("abcdefg");
        logger.writeAndBlock("ABCDEFG");

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(4);

        var content = readTempContent("0000");
        assertEquals("1234567", content);

        content = readTempContent("0001");
        assertEquals("abcdefg", content);

        content = readTempContent("0002");
        assertEquals("ABCDEFG", content);
    }

    @Test
    public void createMappedByteBufferLoggerWriteOrDrop() throws Throwable {
        tempPath = Files.createTempDirectory("test-createMappedByteBufferLoggerWriteOrDrop");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 1);
        var res = logger.writeOrDrop("1234567");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());
        res = logger.writeOrDrop("abcdefg");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.DROP_PENDING, res.type());
        res.writablePromise().block();
        res = logger.writeOrDrop("abcdefg");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());
        res = logger.writeOrDrop("ABCDEFG");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.DROP_PENDING, res.type());
        res.writablePromise().block();
        res = logger.writeOrDrop("ABCDEFG");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(4);

        var content = readTempContent("0000");
        assertEquals("1234567", content);

        content = readTempContent("0001");
        assertEquals("abcdefg", content);

        content = readTempContent("0002");
        assertEquals("ABCDEFG", content);
    }

    @Test
    public void randomTestMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-randomTestMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";
        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 16384, 4096);

        var expected = new StringBuilder();

        var random = new Random();
        for (int i = 0; i < 16384; ++i) {
            int length = random.nextInt(4096) + 1;
            var bytes = new byte[length];
            random.nextBytes(bytes);
            for (var x = 0; x < length; ++x) {
                bytes[x] = (byte) ((bytes[x] % 26) + 97);
            }

            var s = new String(bytes, StandardCharsets.UTF_8);
            expected.append(s);
            logger.writeAndBlock(s);
        }

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        var ls = new ArrayList<>(List.of(files));
        ls.sort(Comparator.comparing(File::getName));

        var sb = new StringBuilder();
        for (var f : ls) {
            var bytes = Files.readAllBytes(f.toPath());
            if (bytes[bytes.length - 1] != 0) {
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            } else {
                sb.append(MemorySegment.ofArray(bytes).getString(0));
            }
        }
        assertEquals(expected.toString(), sb.toString());
        assertTrue(expected.length() > (4096d / 2 * 16384 * 0.8));
        assertTrue(expected.length() < (4096d / 2 * 16384 * 1.2));
    }

    @Test
    public void randomAccessFileByteArray() throws Exception {
        var p = Files.createTempFile("randomAccessFileByteArray", ".txt");
        p.toFile().deleteOnExit();
        Files.write(p, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
        try (var arr = new RandomAccessFileByteArray(p)) {
            for (int i = 0; i < arr.length(); ++i) {
                assertEquals(i + 1, arr.get(i));
            }
            for (int i = arr.length() - 1; i >= 0; --i) {
                assertEquals(i + 1, arr.get(i));
            }

            var helloworld = "hello world".getBytes();
            for (int i = 0; i < helloworld.length; ++i) {
                arr.set(i, helloworld[i]);
            }
            var str = Files.readString(p);
            assertEquals("hello world", str);

            for (int i = 0; i < helloworld.length; ++i) {
                arr.set(arr.length() - i - 1, helloworld[i]);
            }
            str = Files.readString(p);
            assertEquals("dlrow olleh", str);

            var jarr = arr.toJavaArray();
            assertArrayEquals("dlrow olleh".getBytes(), jarr);

            var buf = ByteBuffer.allocate(4);
            buf.position(1);
            arr.byteBufferPut(buf, 3, 2);
            assertEquals(3, buf.position());
            assertEquals(4, buf.limit());
            assertEquals('o', buf.array()[1]);
            assertEquals('w', buf.array()[2]);

            buf = ByteBuffer.allocate(11);
            buf.put("abcdefghijk".getBytes());
            buf.flip();
            arr.byteBufferGet(buf, 0, 11);
            str = Files.readString(p);
            assertEquals("abcdefghijk", str);
        }
    }

    @Test
    public void concurrentObjectPoolPoll() {
        var pool = new ConcurrentObjectPool<Integer>(65536, 16, 0);
        for (int i = 0; i < 65536; ++i) {
            assertTrue("add(" + i + ")", pool.add(i));
        }
        // pool is full, adding should fail now
        for (int i = 0; i < 10; ++i) {
            assertFalse(pool.add(1234));
        }
        var results = new ArrayList<Integer>();
        var pollCount = new ArrayList<Integer>();
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < 16; ++i) {
            threads.add(new Thread(() -> testConcurrentObjectPoolPoll(pool, results, pollCount)));
        }
        for (var t : threads) {
            t.start();
        }
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }
        assertEquals(65536, results.size());
        assertEquals(65536, new HashSet<>(results).size());
        results.sort(Integer::compareTo);
        assertEquals(0, results.getFirst().intValue());
        assertEquals(65535, results.getLast().intValue());
        int sum = 0;
        for (var n : pollCount) {
            sum += n;
        }
        assertEquals(65536, sum);
        for (int i = 0; i < 65536; ++i) {
            assertTrue("check:add(" + i + ")", pool.add(i));
        }
    }

    private void testConcurrentObjectPoolPoll(ConcurrentObjectPool<Integer> pool, List<Integer> results, List<Integer> pollCount) {
        var resultsOfThisThread = new ArrayList<Integer>();
        while (true) {
            var e = pool.poll();
            if (e == null) {
                break;
            }
            resultsOfThisThread.add(e);
            Thread.yield();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (results) {
            results.addAll(resultsOfThisThread);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (pollCount) {
            pollCount.add(resultsOfThisThread.size());
        }
    }

    @Test
    public void concurrentObjectPoolAdd() {
        var pool = new ConcurrentObjectPool<Integer>(65536, 4, 0);

        var added = new ArrayList<Integer>();
        var addCount = new ArrayList<Integer>();
        var threads = new ArrayList<VProxyThread>();
        for (int i = 0; i < 64; ++i) {
            threads.add(VProxyThread.create(() -> testConcurrentObjectPoolAdd(pool, added, addCount), "add:" + i));
        }
        for (var t : threads) {
            t.start();
        }
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }
        assertEquals(65536, added.size());
        added.sort(Integer::compareTo);
        var results = new ArrayList<Integer>();
        for (int i = 0; i < 65536; ++i) {
            var e = pool.poll();
            assertNotNull("check:poll():" + i, e);
            results.add(e);
        }
        // the pool is empty so nothing would be polled
        for (int i = 0; i < 10; ++i) {
            assertNull(pool.poll());
        }
        results.sort(Integer::compareTo);
        assertEquals(added, results);

        int cnt = 0;
        for (var n : addCount) {
            cnt += n;
        }
        assertEquals(65536, cnt);
    }

    private void testConcurrentObjectPoolAdd(ConcurrentObjectPool<Integer> pool, List<Integer> added, List<Integer> addCount) {
        var addedOfThisThread = new ArrayList<Integer>();
        while (true) {
            var e = ThreadLocalRandom.current().nextInt();
            if (!pool.add(e)) {
                break;
            }
            addedOfThisThread.add(e);
            Thread.yield();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (added) {
            added.addAll(addedOfThisThread);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (addCount) {
            addCount.add(addedOfThisThread.size());
        }
    }
}
