package vproxy.processor.http1.builder;

import vproxy.processor.http1.entity.Request;
import vproxy.util.ByteArray;

import java.util.LinkedList;
import java.util.List;

public class RequestBuilder {
    public StringBuilder method = new StringBuilder();
    public StringBuilder uri = new StringBuilder();
    public StringBuilder version;
    public List<HeaderBuilder> headers;
    public ByteArray body;
    public List<ChunkBuilder> chunks;
    public List<HeaderBuilder> trailers;

    @SuppressWarnings("Duplicates")
    public Request build() {
        Request req = new Request();
        req.method = method.toString();
        req.uri = uri.toString();
        if (version != null) {
            req.version = version.toString();
        }
        if (headers != null) {
            req.headers = new LinkedList<>();
            for (var h : headers) {
                req.headers.add(h.build());
            }
        }
        if (body != null) {
            req.body = body.copy();
        }
        if (chunks != null) {
            req.chunks = new LinkedList<>();
            for (var c : chunks) {
                req.chunks.add(c.build());
            }
        }
        if (trailers != null) {
            req.trailers = new LinkedList<>();
            for (var h : trailers) {
                req.trailers.add(h.build());
            }
        }
        return req;
    }

    @Override
    public String toString() {
        return "RequestBuilder{" +
            "method=" + method +
            ", uri=" + uri +
            ", version=" + version +
            ", headers=" + headers +
            ", body=" + body +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
