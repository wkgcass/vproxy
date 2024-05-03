package io.vproxy.base.processor.http1;

import io.vproxy.base.processor.OOProcessor;

public class HttpProcessor extends OOProcessor<HttpContext, HttpSubContext> {
    @Override
    public String name() {
        return "http/1.x";
    }

    @Override
    public String[] alpn() {
        return new String[]{"http/1.1", "http/1.0"};
    }

    @Override
    public HttpContext init(ContextInitParams params) {
        return new HttpContext(params.clientAddress());
    }

    @Override
    public HttpSubContext initSub(SubContextInitParams<HttpContext> params) {
        return new HttpSubContext(params.ctx(), params.id(), params.delegate());
    }
}
