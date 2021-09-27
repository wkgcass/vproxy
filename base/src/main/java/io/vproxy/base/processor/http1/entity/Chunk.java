package io.vproxy.base.processor.http1.entity;

import io.vproxy.base.util.ByteArray;

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

    public ByteArray toByteArray() {
        String ext = "";
        if (extension != null && !extension.isBlank()) {
            ext = ";" + extension;
        }
        ByteArray ret = ByteArray.from((Integer.toHexString(size) + ext + "\r\n").getBytes());
        if (size == 0) {
            return ret;
        }
        return ret.concat(content).concat(ByteArray.from("\r\n".getBytes()));
    }
}
