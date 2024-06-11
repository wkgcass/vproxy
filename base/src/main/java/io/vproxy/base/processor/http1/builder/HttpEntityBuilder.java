package io.vproxy.base.processor.http1.builder;

import io.vproxy.base.processor.http1.entity.HttpEntity;
import io.vproxy.base.util.ByteArray;

import java.util.LinkedList;
import java.util.List;

public abstract class HttpEntityBuilder {
    public List<HeaderBuilder> headers;
    public ByteArray body;
    public List<ChunkBuilder> chunks;
    public List<HeaderBuilder> trailers;

    // for state machine
    public int dataLength = -1;
    public boolean isChunked = false;
    public ChunkBuilder chunk;
    // for processor
    public String lastHostHeader = null;

    protected void fillCommonPart(HttpEntity entity) {
        if (headers != null) {
            entity.headers = new LinkedList<>();
            for (var h : headers) {
                entity.headers.add(h.build());
            }
        }
        if (body != null) {
            entity.body = body.copy();
        }
        if (chunks != null) {
            entity.chunks = new LinkedList<>();
            for (var c : chunks) {
                entity.chunks.add(c.build());
            }
        }
        if (trailers != null) {
            entity.trailers = new LinkedList<>();
            for (var h : trailers) {
                entity.trailers.add(h.build());
            }
        }
    }

    public ByteArray trailersToByteArray() {
        if (trailers == null || trailers.isEmpty())
            return ByteArray.allocate(0);
        var sb = new StringBuilder();
        for (var h : trailers) {
            sb.append(h.keyAsString()).append(": ").append(h.valueAsString()).append("\r\n");
        }
        return ByteArray.from(sb.toString());
    }

    abstract public HttpEntity build();
}
