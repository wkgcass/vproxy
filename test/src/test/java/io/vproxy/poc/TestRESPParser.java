package io.vproxy.poc;

import io.vproxy.base.redis.RESPParser;
import io.vproxy.base.redis.entity.*;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.nio.charset.StandardCharsets;

public class TestRESPParser {
    public static void main(String[] args) throws Exception {
        {
            RESPString string = (RESPString) parse("+OK\r\n");
            System.out.println(string);
            ByteArray result = (ByteArray) string.getJavaObject();
            if (!result.toString().equals("OK"))
                throw new Exception("wrong simple string");
        }
        {
            RESPError error = (RESPError) parse("-NOAUTH\r\n");
            System.out.println(error);
            if (!error.getJavaObject().toString().contains("NOAUTH"))
                throw new Exception("wrong simple error");
        }
        {
            RESPInteger integer = (RESPInteger) parse(":123\r\n");
            System.out.println(integer);
            if (integer.integer != 123)
                throw new Exception("wrong integer");
        }
        {
            RESPInteger negInt = (RESPInteger) parse(":-123\r\n");
            System.out.println(negInt);
            if (negInt.integer != -123)
                throw new Exception("wrong integer");
        }
        {
            RESPInline inline = (RESPInline) parse("PING\r\n");
            System.out.println(inline);
            ByteArray result = (ByteArray) inline.getJavaObject();
            if (!result.toString().equals("PING"))
                throw new Exception("wrong inline");
        }
        {
            RESPBulkString empty = (RESPBulkString) parse("$0\r\n\r\n");
            System.out.println(empty);
            ByteArray result = (ByteArray) empty.getJavaObject();
            if (result.length() != 0)
                throw new Exception("wrong empty bulk string");
        }
        {
            RESPBulkString nil = (RESPBulkString) parse("$-1\r\n");
            System.out.println(nil);
            if (nil.data != null)
                throw new Exception("wrong null bulk string");
        }
        {
            RESPBulkString blk = (RESPBulkString) parse("$6\r\nfoobar\r\n");
            System.out.println(blk);
            ByteArray result = (ByteArray) blk.getJavaObject();
            if (!result.toString().equals("foobar"))
                throw new Exception("wrong bulk string");
        }
        {
            RESPArray emptyArray = (RESPArray) parse("*0\r\n");
            System.out.println(emptyArray);
            if (!emptyArray.array.isEmpty())
                throw new Exception("wrong empty array");
        }
        {
            RESPArray array = (RESPArray) parse("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n");
            System.out.println(array);
            if (array.array.size() != 2)
                throw new Exception("wrong array.len");
            if (!((ByteArray) ((RESPBulkString) array.array.get(0)).getJavaObject()).toString().equals("foo"))
                throw new Exception("wrong array[0]");
            if (!((ByteArray) ((RESPBulkString) array.array.get(1)).getJavaObject()).toString().equals("bar"))
                throw new Exception("wrong array[1]");
        }
        {
            RESPArray intArr = (RESPArray) parse("*3\r\n:1\r\n:2\r\n:3\r\n");
            System.out.println(intArr);
            if (intArr.array.size() != 3)
                throw new Exception("wrong intArr.len");
            if (((RESPInteger) intArr.array.get(0)).integer != 1)
                throw new Exception("wrong intArr[0]");
            if (((RESPInteger) intArr.array.get(1)).integer != 2)
                throw new Exception("wrong intArr[1]");
            if (((RESPInteger) intArr.array.get(2)).integer != 3)
                throw new Exception("wrong intArr[2]");
        }
        {
            RESPArray mixArr = (RESPArray) parse("" +
                "*5\r\n" +
                ":1\r\n" +
                ":2\r\n" +
                ":3\r\n" +
                ":4\r\n" +
                "$6\r\n" +
                "foobar\r\n");
            System.out.println(mixArr);
            if (mixArr.array.size() != 5)
                throw new Exception("wrong mixArr.len");
            if (((RESPInteger) mixArr.array.get(0)).integer != 1)
                throw new Exception("wrong mixArr[0]");
            if (((RESPInteger) mixArr.array.get(1)).integer != 2)
                throw new Exception("wrong mixArr[1]");
            if (((RESPInteger) mixArr.array.get(2)).integer != 3)
                throw new Exception("wrong mixArr[2]");
            if (((RESPInteger) mixArr.array.get(3)).integer != 4)
                throw new Exception("wrong mixArr[3]");
            if (!((ByteArray) ((RESPBulkString) mixArr.array.get(4)).getJavaObject()).toString().equals("foobar"))
                throw new Exception("wrong mixArr[4]");
        }
        // test binary data in bulk string
        {
            byte[] binaryData = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
            String input = "$5\r\n" + new String(binaryData, "ISO-8859-1") + "\r\n";
            RESPBulkString blk = (RESPBulkString) parse(input);
            System.out.println(blk);
            ByteArray result = (ByteArray) blk.getJavaObject();
            if (result.length() != 5)
                throw new Exception("wrong binary bulk string length");
            for (int i = 0; i < binaryData.length; i++) {
                if (result.get(i) != binaryData[i])
                    throw new Exception("wrong binary bulk string data at index " + i);
            }
        }
        // test binary data in simple string
        {
            byte[] binaryData = new byte[]{0x01, 0x02, 0x03};
            String input = "+" + new String(binaryData, "ISO-8859-1") + "\r\n";
            RESPString str = (RESPString) parse(input);
            System.out.println(str);
            ByteArray result = (ByteArray) str.getJavaObject();
            if (result.length() != 3)
                throw new Exception("wrong binary simple string length");
            for (int i = 0; i < binaryData.length; i++) {
                if (result.get(i) != binaryData[i])
                    throw new Exception("wrong binary simple string data at index " + i);
            }
        }
        System.out.println("all tests passed");
    }

    private static RESP parse(String str) throws Exception {
        RESPParser parser = new RESPParser(16384);
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        RingBuffer rb = RingBuffer.allocate(bytes.length);
        ByteArrayChannel ch = ByteArrayChannel.fromFull(bytes);
        rb.storeBytesFrom(ch);
        int r = parser.feed(rb);
        if (r != 0) {
            if (parser.getErrorMessage() == null)
                throw new Exception("the parsing should have ended");
            throw new Exception("parse failed " + parser.getErrorMessage());
        }
        return parser.getResult();
    }
}
