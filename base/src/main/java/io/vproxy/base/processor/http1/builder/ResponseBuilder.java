package io.vproxy.base.processor.http1.builder;

import io.vproxy.base.processor.http1.entity.Response;

public class ResponseBuilder extends HttpEntityBuilder {
    public StringBuilder version = new StringBuilder();
    public StringBuilder statusCode = new StringBuilder();
    public StringBuilder reason = new StringBuilder();

    @SuppressWarnings("Duplicates")
    public Response build() {
        Response resp = new Response();
        resp.version = version.toString();
        resp.statusCode = Integer.parseInt(statusCode.toString());
        resp.reason = reason.toString();
        fillCommonPart(resp);
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
