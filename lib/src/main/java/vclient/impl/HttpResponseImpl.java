package vclient.impl;

import vclient.HttpClientConn;
import vclient.HttpResponse;
import vproxybase.processor.http1.entity.Chunk;
import vproxybase.processor.http1.entity.Header;
import vproxybase.processor.http1.entity.Response;
import vproxybase.util.ByteArray;

public class HttpResponseImpl implements HttpResponse {
    private final HttpClientConn conn;
    private final Response response;
    private ByteArray generalBody;

    public HttpResponseImpl(HttpClientConn conn, Response response) {
        this.conn = conn;
        this.response = response;
    }

    @Override
    public int status() {
        return response.statusCode;
    }

    @Override
    public String header(String key) {
        String ret = null;
        if (response.headers != null) {
            for (Header h : response.headers) {
                if (h.key.equalsIgnoreCase(key)) {
                    ret = h.value;
                }
            }
        }
        return ret;
    }

    @Override
    public ByteArray body() {
        if (generalBody == null) {
            if (response.body != null) {
                generalBody = response.body;
            } else if (response.chunks != null && !response.chunks.isEmpty()) {
                ByteArray ret = null;
                for (Chunk c : response.chunks) {
                    ByteArray data = c.content;
                    if (data == null) { // maybe the last chunk
                        continue;
                    }
                    if (ret == null) {
                        ret = data;
                    } else {
                        ret = ret.concat(data);
                    }
                }
                generalBody = ret;
            }
        }
        return generalBody;
    }

    @Override
    public HttpClientConn conn() {
        return conn;
    }

    @Override
    public String toString() {
        return response.toString();
    }
}
