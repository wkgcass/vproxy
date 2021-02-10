package vproxy.test.cases;

import org.junit.Test;
import vproxybase.util.ByteArray;
import vproxybase.util.bytearray.ByteArrayBuilder;

import static org.junit.Assert.*;

public class TestByteArrayBuilder {
    @Test
    public void construct() {
        // default arguments
        testEquals(new ByteArrayBuilder(), 12);

        // dynamic arguments
        for (int i = 1; i < 24; i ++){
            for (int j = 1; j < 24; j ++){
                testEquals(new ByteArrayBuilder(i), j);
            }
        }
    }

    private void testEquals(ByteArrayBuilder builder, int dataLen) {
        int[] bytes = new int[dataLen];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] =  i + 0x30;
        }

        builder.append(bytes);
        assertEquals(dataLen, builder.length());
        assertEquals(0x30, builder.get(0));
        builder.set(0, (byte) 0x34);
        assertEquals(0x34, builder.get(0));
        builder.set(0, (byte) 0x30);

        var expected = ByteArray.from(bytes);
        assertEquals(expected, builder);
        assertTrue(expected.equalsIgnoreCase(builder));
        assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
    }

    @Test(expected = RuntimeException.class)
    public void constructWithException(){
        new ByteArrayBuilder(0);
    }

    @Test
    public void equalsIgnoreCaseAfterTrim() {
        { // non-trim
            var builder = new ByteArrayBuilder(2).append(0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39);
            var expected = ByteArray.from(0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39);
            assertTrue(expected.equalsIgnoreCase(builder));
            assertEquals(expected.trim(), builder.trim());
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }

        { // trim before and after, total
            var builder = new ByteArrayBuilder(2).append(' ', 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, ' ');
            var expected = ByteArray.from(0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39);
            assertFalse(expected.equalsIgnoreCase(builder));
            assertEquals(expected.trim(), builder.trim());
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }

        { // trim before and after, A
            var builder = new ByteArrayBuilder(3).append(0x41, 0x42, 0x61, 0x62, ' ');
            var expected = ByteArray.from('\t', 0x61, 0x62, 0x41, 0x42);
            assertFalse(expected.equalsIgnoreCase(builder));
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }

        { // trim before and after, B
            var builder = new ByteArrayBuilder(3).append(' ', 0x41, 0x42, 0x61, 0x62);
            var expected = ByteArray.from(0x61, 0x62, 0x41, 0x42, ' ');
            assertFalse(expected.equalsIgnoreCase(builder));
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }

        { // both trim before
            var builder = new ByteArrayBuilder(3).append(' ', 0x41, 0x42, 0x61, 0x62);
            var expected = ByteArray.from('\n', 0x61, 0x62, 0x41, 0x42);
            assertFalse(expected.equalsIgnoreCase(builder));
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }

        { // both trim after
            var builder = new ByteArrayBuilder(3).append(0x41, 0x42, 0x61, 0x62, ' ');
            var expected = ByteArray.from(0x61, 0x62, 0x41, 0x42, '\n');
            assertFalse(expected.equalsIgnoreCase(builder));
            assertTrue(expected.equalsIgnoreCaseAfterTrim(builder));
        }
    }


}
