package vproxy.processor.http.builder;

import vproxy.processor.http.entity.Response;
import vproxy.util.ByteArray;

import java.util.LinkedList;
import java.util.List;

public class ResponseBuilder {
    public StringBuilder version = new StringBuilder();
    public StringBuilder statusCode = new StringBuilder();
    public StringBuilder reason = new StringBuilder();
    public List<HeaderBuilder> headers;
    public ByteArray body;
    public List<ChunkBuilder> chunks;
    public List<HeaderBuilder> trailers;

    @SuppressWarnings("Duplicates")
    public Response build() {
        Response resp = new Response();
        resp.version = version.toString();
        resp.statusCode = Integer.parseInt(statusCode.toString());
        resp.reason = reason.toString();
        if (headers != null) {
            resp.headers = new LinkedList<>();
            for (var h : headers) {
                resp.headers.add(h.build());
            }
        }
        if (body != null) {
            resp.body = body.copy();
        }
        if (chunks != null) {
            resp.chunks = new LinkedList<>();
            for (var c : chunks) {
                resp.chunks.add(c.build());
            }
        }
        if (trailers != null) {
            resp.trailers = new LinkedList<>();
            for (var h : trailers) {
                resp.trailers.add(h.build());
            }
        }
        return resp;
    }

    @Override
    public String toString() {
        return "ResponseBuilder{" +
            "version=" + version +
            ", statusCode=" + statusCode +
            ", reason=" + reason +
            ", headers=" + headers +
            ", body=" + body +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
