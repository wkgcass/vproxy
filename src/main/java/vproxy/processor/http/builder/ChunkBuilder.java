package vproxy.processor.http.builder;

import vproxy.processor.http.entity.Chunk;
import vproxy.util.ByteArray;

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
}
