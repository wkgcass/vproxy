package vproxy.base.processor.http1.entity;

import vproxy.base.util.ByteArray;

import java.util.List;

public class Request {
    public String method; // notnull
    public String uri; // notnull
    public String version; // nullable
    public List<Header> headers; // nullable
    public ByteArray body; // nullable
    public boolean isPlain = false;

    public List<Chunk> chunks; // nullable
    public List<Header> trailers; // nullable

    @SuppressWarnings("DuplicatedCode")
    public ByteArray toByteArray() {
        StringBuilder textPart = new StringBuilder();
        textPart.append(method).append(" ").append(uri);
        if (version != null) {
            textPart.append(" ").append(version);
        }
        textPart.append("\r\n"); // end first line
        // the following should be the same as Response
        boolean usingGZip = false;
        if (isPlain && headers != null) { // encode only if body is plain
            for (Header h : headers) {
                if (h.key.trim().equalsIgnoreCase("content-encoding") && h.value.equalsIgnoreCase("gzip")) {
                    usingGZip = true;
                    break;
                }
            }
        }
        if (headers != null) {
            for (Header h : headers) {
                if (usingGZip && h.key.trim().equalsIgnoreCase("content-length")) {
                    continue; // skip content-length, we will calculate it later
                }
                textPart.append(h.key).append(": ").append(h.value).append("\r\n");
            }
        }
        ByteArray body = this.body;
        if (body != null) {
            if (usingGZip) {
                body = ByteArray.from(body.toGZipJavaByteArray());
                textPart.append("Content-Length: ").append(body.length()).append("\r\n");
            }
        }
        textPart.append("\r\n");
        ByteArray ret = ByteArray.from(textPart.toString().getBytes());
        if (body != null) {
            ret = ret.concat(body);
        }
        if (chunks != null) {
            for (Chunk ch : chunks) {
                ret = ret.concat(ch.toByteArray());
            }
        }
        if (trailers != null) {
            textPart = new StringBuilder();
            for (Header h : trailers) {
                textPart.append(h.key).append(": ").append(h.value).append("\r\n");
            }
            ret = ret.concat(ByteArray.from(textPart.toString()));
        }
        if (chunks != null || trailers != null) {
            ret = ret.concat(ByteArray.from("\r\n".getBytes()));
        }
        return ret.arrange();
    }

    @Override
    public String toString() {
        return "Request{" +
            "method='" + method + '\'' +
            ", uri='" + uri + '\'' +
            ", version='" + version + '\'' +
            ", headers=" + headers +
            ", body=" + body +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
