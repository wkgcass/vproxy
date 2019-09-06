package vclient.impl;

import vclient.HttpResponse;
import vproxy.processor.http1.entity.Header;
import vproxy.processor.http1.entity.Response;
import vproxy.util.ByteArray;

public class HttpResponseImpl implements HttpResponse {
    private final Response response;

    public HttpResponseImpl(Response response) {
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
        return response.body;
    }

    @Override
    public String toString() {
        return response.toString();
    }
}
