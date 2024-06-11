package io.vproxy.base.http;

import io.vproxy.base.processor.http1.builder.HttpEntityBuilder;
import io.vproxy.base.util.nio.ByteArrayChannel;

public interface IHttpParser {
    int nextState();

    HttpEntityBuilder getBuilder();

    int getState();

    int feed(ByteArrayChannel chnl);

    String getErrorMessage();
}
