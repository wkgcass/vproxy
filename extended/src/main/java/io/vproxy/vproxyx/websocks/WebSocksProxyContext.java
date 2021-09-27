package io.vproxy.vproxyx.websocks;

import io.vproxy.base.connection.Connector;
import io.vproxy.base.http.HttpContext;
import io.vproxy.base.protocol.ProtocolHandlerContext;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.socks.Socks5ProxyContext;

import java.io.IOException;

public class WebSocksProxyContext {
    int step = 0;
    // 1: http
    // 2: websocks
    // 3: socks5
    // 4: large file

    final ProtocolHandlerContext<HttpContext> httpContext;
    ByteArrayChannel webSocketBytes;
    final ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> socks5Context;

    public WebSocksProxyContext(ProtocolHandlerContext<HttpContext> httpContext,
                                ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> socks5Context) {
        this.httpContext = httpContext;
        this.socks5Context = socks5Context;
    }
}
