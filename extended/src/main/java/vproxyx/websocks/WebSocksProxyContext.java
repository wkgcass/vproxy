package vproxyx.websocks;

import vproxy.base.connection.Connector;
import vproxy.base.http.HttpContext;
import vproxy.base.protocol.ProtocolHandlerContext;
import vproxy.base.util.Callback;
import vproxy.base.util.Tuple;
import vproxy.base.util.nio.ByteArrayChannel;
import vproxy.socks.Socks5ProxyContext;

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
