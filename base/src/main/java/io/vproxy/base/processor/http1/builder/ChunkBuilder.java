package io.vproxy.base.processor.http1.builder;

import io.vproxy.base.processor.http1.entity.Chunk;
import io.vproxy.base.util.ByteArray;

public class ChunkBuilder {
    public StringBuilder size = new StringBuilder();
    public StringBuilder extension;
    public ByteArray content;

    public Chunk build() {
        Chunk c = new Chunk();
        c.size = Integer.parseInt(size.toString().trim(), 16);
        if (extension != null) {
            c.extension = extension.toString().trim();
        }
        if (content != null) {
            c.content = content.copy();
        }
        return c;
    }

    @Override
    public String toString() {
        return "ChunkBuilder{" +
               "size=" + size +
               ", extension=" + extension +
               ", content=" + content +
               '}';
    }

    public ByteArray headToByteArray() {
        int charLength = size.length();
        if (extension != null) {
            charLength += 1;
            charLength += extension.length();
        }
        charLength += 2;
        char[] chars = new char[charLength];
        size.getChars(0, size.length(), chars, 0);
        if (extension != null) {
            chars[size.length()] = ';';
            extension.getChars(0, extension.length(), chars, size.length() + 1);
        }
        chars[charLength - 2] = '\r';
        chars[charLength - 1] = '\n';
        return ByteArray.from(new String(chars).getBytes());
    }
}
