package vproxyx.websocks;

import vproxy.connection.Connector;
import vproxy.http.HttpContext;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.socks.Socks5ProxyContext;
import vproxy.util.nio.ByteArrayChannel;
import vproxy.util.Callback;
import vproxy.util.Tuple;

import java.io.IOException;

public class WebSocksProxyContext {
    int step = 0;

    final ProtocolHandlerContext<HttpContext> httpContext;
    ByteArrayChannel webSocketBytes;
    final ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> socks5Context;

    public WebSocksProxyContext(ProtocolHandlerContext<HttpContext> httpContext,
                                ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> socks5Context) {
        this.httpContext = httpContext;
        this.socks5Context = socks5Context;
    }
}
