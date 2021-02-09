package vproxy.test.cases;

import org.junit.Test;
import vproxybase.util.ByteArray;
import vproxybase.util.bytearray.ByteArrayBuilder;

import static org.junit.Assert.*;

public class TestByteArrayBuilder {
    @Test
    public void construct() {
        var builder = new ByteArrayBuilder(4);
        builder.append(0x30, 0x31, 0x32, 0x33, 0x30, 0x35, 0x36, 0x37, 0x38, 0x39);
        assertEquals(10, builder.length());
        assertEquals(0x30, builder.get(4));
        builder.set(4, (byte) 0x34);
        assertEquals(0x34, builder.get(4));

        var expected = ByteArray.from(0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39);
        assertEquals(expected, builder);
        assertTrue(expected.equalsIgnoreCase(builder));
        assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
    }

    @Test
    public void equalsIgnoreCaseAfterTrim() {
        {
            var builder = new ByteArrayBuilder(2).append(' ', 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, ' ');
            var expected = ByteArray.from(0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39);
            assertFalse(expected.equalsIgnoreCase(builder));
            assertEquals(expected.trim(), builder.trim());
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }

        {
            var builder = new ByteArrayBuilder(3).append(' ', 0x41, 0x42, 0x61, 0x62, ' ');
            var expected = ByteArray.from('\t', 0x61, 0x62, 0x41, 0x42, '\n');
            assertFalse(expected.equalsIgnoreCase(builder));
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }
    }


}
