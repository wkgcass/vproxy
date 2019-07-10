package vproxy.processor.http.entity;

import vproxy.util.ByteArray;

import java.util.List;

public class Response {
    public String version; // notnull
    public int statusCode; // notnull
    public String reason; // notnull
    public List<Header> headers; // nullable
    public ByteArray body; // nullable

    public List<Chunk> chunks; // nullable
    public List<Header> trailers; // nullable

    @Override
    public String toString() {
        return "Response{" +
            "version='" + version + '\'' +
            ", statusCode=" + statusCode +
            ", reason='" + reason + '\'' +
            ", headers=" + headers +
            ", body=" + body +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
