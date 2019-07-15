package vproxy.processor.http1.entity;

import vproxy.util.ByteArray;

public class Chunk {
    public int size; // notnull
    public String extension; // nullable
    public ByteArray content; // nullable

    @Override
    public String toString() {
        return "Chunk{" +
            "size=" + size +
            ", extension='" + extension + '\'' +
            ", content=" + content +
            '}';
    }
}
