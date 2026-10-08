package io.vproxy.vswitch.iface;

import io.vproxy.base.util.ByteArray;
import org.junit.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TestXdpPacketCopy {
    private static void check(byte[] bytes, int shape, int headroom, Arena arena) {
        ByteArray heap = ByteArray.from(bytes.clone());
        MemorySegment nativeSource = arena.allocate(bytes.length + 10L, 1);
        nativeSource.asSlice(5, bytes.length).copyFrom(MemorySegment.ofArray(bytes));
        ByteArray memory = ByteArray.from(nativeSource.asReadOnly()).sub(5, bytes.length);
        ByteArray source;
        if (shape == 0) {
            source = heap;
        } else if (shape == 1) {
            source = memory;
        } else {
            int l2 = Math.min(14, bytes.length);
            int headers = Math.min(40, bytes.length - l2);
            ByteArray payload = shape == 2 ? heap : memory;
            source = heap.sub(0, l2).concat(heap.sub(l2, headers))
                .concat(payload.sub(l2 + headers, bytes.length - l2 - headers));
        }
        MemorySegment chunk = arena.allocate(headroom + bytes.length + 32L, 1);
        chunk.fill((byte) 0x55);
        // Leave Ethernet/IP headroom and checksum metadata outside the payload.
        MemorySegment target = chunk.asSlice(headroom, bytes.length);
        XDPIface.copyPacketData(source, target);
        assertArrayEquals(bytes, target.toArray(ValueLayout.JAVA_BYTE));
        assertArrayEquals(bytes, source.toJavaArray());
        for (int i = 0; i < headroom; ++i) {
            assertEquals(0x55, chunk.get(ValueLayout.JAVA_BYTE, i));
        }
        for (int i = 0; i < 32; ++i) {
            assertEquals(0x55, chunk.get(ValueLayout.JAVA_BYTE, headroom + bytes.length + i));
        }
    }

    @Test
    public void packetBoundariesPreserveNativeChunkGuards() {
        Random random = new Random(0x58d0);
        try (Arena arena = Arena.ofConfined()) {
            for (int size : new int[]{0, 1, 13, 14, 20, 53, 54, 64, 512, 1499, 1500, 1501, 4096}) {
                byte[] bytes = new byte[size];
                random.nextBytes(bytes);
                for (int shape = 0; shape < 4; ++shape) {
                    for (int headroom : new int[]{0, 32, 64}) {
                        check(bytes, shape, headroom, arena);
                    }
                }
            }
        }
    }

    @Test
    public void repeatedPacketCopiesHaveIndependentNativeTargets() {
        Random random = new Random(0xc0ffee);
        try (Arena arena = Arena.ofConfined()) {
            for (int round = 0; round < 128; ++round) {
                byte[] bytes = new byte[1 + random.nextInt(4096)];
                random.nextBytes(bytes);
                check(bytes, round % 4, round % 2 == 0 ? 32 : 64, arena);
            }
        }
    }
}
