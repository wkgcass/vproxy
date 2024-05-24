package io.vproxy.base.processor.http1.builder;

import io.vproxy.base.processor.http1.entity.Request;

public class RequestBuilder extends HttpEntityBuilder {
    public StringBuilder method = new StringBuilder();
    public StringBuilder uri = new StringBuilder();
    public StringBuilder version;

    @SuppressWarnings("Duplicates")
    public Request build() {
        Request req = new Request();
        req.method = method.toString();
        req.uri = uri.toString();
        if (version != null) {
            req.version = version.toString();
        }
        fillCommonPart(req);
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
