package vproxybase.processor.http1.entity;

import vproxybase.util.ByteArray;

import java.util.List;

public class Response {
    public String version; // notnull
    public int statusCode; // notnull
    public String reason; // notnull
    public List<Header> headers; // nullable
    public ByteArray body; // nullable
    public boolean isPlain = false;

    public List<Chunk> chunks; // nullable
    public List<Header> trailers; // nullable

    @SuppressWarnings("DuplicatedCode")
    public ByteArray toByteArray() {
        StringBuilder textPart = new StringBuilder();
        textPart.append(version).append(" ").append(statusCode).append(" ").append(reason).append("\r\n");
        // the following should be the same as Request
        boolean usingGZip = false;
        if (headers != null) {
            for (Header h : headers) {
                if (h.key.trim().equalsIgnoreCase("content-length")) {
                    continue;
                }
                if (h.key.trim().equalsIgnoreCase("content-encoding") && h.value.equalsIgnoreCase("gzip")) {
                    usingGZip = true;
                }
                textPart.append(h.key).append(": ").append(h.value).append("\r\n");
            }
        }
        ByteArray body = this.body;
        if (body != null) {
            if (isPlain && usingGZip) {
                body = ByteArray.from(body.toGZipJavaByteArray());
            }
            textPart.append("Content-Length: ").append(body.length()).append("\r\n");
        }
        textPart.append("\r\n");
        ByteArray ret = ByteArray.from(textPart.toString().getBytes());
        if (body != null) {
            ret = ret.concat(body);
        }
        if (chunks != null) {
            for (Chunk ch : chunks) {
                ByteArray chBytes = ByteArray.from((Integer.toHexString(ch.size) + ";" + ch.extension + "\r\n").getBytes());
                if (ch.size != 0) {
                    chBytes = chBytes.concat(ch.content);
                    chBytes = chBytes.concat(ByteArray.from("\r\n".getBytes()));
                }
                ret = ret.concat(chBytes);
            }
        }
        if (trailers != null) {
            textPart = new StringBuilder();
            for (Header h : trailers) {
                textPart.append(h.key).append(": ").append(h.value).append("\r\n");
            }
        }
        if (chunks != null || trailers != null) {
            ret = ret.concat(ByteArray.from("\r\n".getBytes()));
        }
        return ret.arrange();
    }

    @Override
    public String toString() {
        return "Response{" +
            "version='" + version + '\'' +
            ", statusCode=" + statusCode +
            ", reason='" + reason + '\'' +
            ", headers=" + headers +
            ", body=" + body +
            ", isPlain=" + isPlain +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
