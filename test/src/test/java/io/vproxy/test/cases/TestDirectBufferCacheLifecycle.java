package io.vproxy.test.cases;

import io.vproxy.base.util.OS;
import io.vproxy.base.util.direct.DirectByteBuffer;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class TestDirectBufferCacheLifecycle {
    private static void borrow(List<DirectByteBuffer> live, int count, int size) {
        for (int i = 0; i < count; ++i) live.add(DirectMemoryUtils.allocateDirectBuffer(size));
    }

    private static void independent(List<DirectByteBuffer> live) {
        var handles = new IdentityHashMap<DirectByteBuffer, Boolean>();
        var buffers = new IdentityHashMap<ByteBuffer, Boolean>();
        for (int i = 0; i < live.size(); ++i) {
            var handle = live.get(i);
            assertNull("duplicate live handle", handles.put(handle, true));
            assertNotNull("live buffer has been cleaned", handle.realBuffer());
            assertNull("duplicate live backing buffer", buffers.put(handle.realBuffer(), true));
            handle.realBuffer().putLong(0, i + 1);
        }
        for (int i = 0; i < live.size(); ++i) {
            assertEquals("another borrower overwrote my data", i + 1, live.get(i).realBuffer().getLong(0));
        }
    }

    private static void release(List<DirectByteBuffer> live) {
        for (var buffer : live) buffer.clean(false);
    }

    @Test
    public void repeatedReturnDoesNotCreateMultipleOwners() {
        assumeFalse(OS.isIOS());
        var retired = DirectMemoryUtils.allocateDirectBuffer(16);
        var live = new ArrayList<DirectByteBuffer>();
        try {
            for (int i = 0; i < 16; ++i) retired.clean();
            borrow(live, 128, 16);
            independent(live);
        } finally {
            release(live);
            retired.clean(false);
        }
    }

    @Test
    public void oldOwnerCannotReturnAnActiveBorrower() {
        assumeFalse(OS.isIOS());
        var warm = new ArrayList<DirectByteBuffer>();
        var live = new ArrayList<DirectByteBuffer>();
        borrow(warm, 16, 32);
        var retired = warm.get(0);
        var backing = retired.realBuffer();
        try {
            for (var buffer : warm) buffer.clean();
            borrow(live, 128, 32);
            assertTrue("exercise actual cached backing reuse", live.stream().anyMatch(x -> x.realBuffer() == backing));
            for (int i = 0; i < 16; ++i) retired.clean();
            borrow(live, 128, 32);
            independent(live);
        } finally {
            release(live);
            release(warm);
        }
    }

    @Test
    public void cacheTransferRetiresHandleAndResetsBorrowedBuffer() {
        assumeFalse(OS.isIOS());
        var warm = new ArrayList<DirectByteBuffer>();
        var live = new ArrayList<DirectByteBuffer>();
        borrow(warm, 16, 64);
        var backing = warm.get(0).realBuffer();
        try {
            for (var buffer : warm) {
                buffer.limit(12).position(8);
                buffer.clean();
                assertNull("released handle stays retired", buffer.realBuffer());
            }
            borrow(live, 128, 64);
            assertTrue("exercise actual cache hit", live.stream().anyMatch(x -> x.realBuffer() == backing));
            for (var buffer : live) {
                assertEquals(0, buffer.position());
                assertEquals(64, buffer.limit());
                assertEquals(64, buffer.getMemorySegment().byteSize());
            }
            independent(live);
            for (var buffer : warm) buffer.clean(false);
            independent(live);
        } finally {
            release(live);
            release(warm);
        }
    }

    @Test
    public void uncachedReleaseIsStillIdempotent() {
        for (int size : new int[]{15, 16, 64}) {
            var buffer = DirectMemoryUtils.allocateDirectBuffer(size);
            buffer.clean(false);
            assertNull(buffer.realBuffer());
            buffer.clean();
            buffer.clean(false);
            assertNull(buffer.realBuffer());
        }
    }
}
