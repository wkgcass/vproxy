package io.vproxy.test.cases;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.SimpleRingBuffer;
import io.vproxy.base.util.ringbuffer.SimpleRingBufferReaderCommitter;
import org.junit.Test;

import java.lang.foreign.ValueLayout;

import static org.junit.Assert.*;

public class TestUtils {
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
}
