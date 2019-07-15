package vproxy.processor.http1.entity;

import vproxy.util.ByteArray;

import java.util.List;

public class Request {
    public String method; // notnull
    public String uri; // notnull
    public String version; // nullable
    public List<Header> headers; // nullable
    public ByteArray body; // nullable

    public List<Chunk> chunks; // nullable
    public List<Header> trailers; // nullable

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
