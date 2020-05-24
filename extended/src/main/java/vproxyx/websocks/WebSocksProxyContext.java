package vproxyx.websocks;

import vproxybase.connection.Connector;
import vproxybase.http.HttpContext;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxy.socks.Socks5ProxyContext;
import vproxybase.util.Callback;
import vproxybase.util.Tuple;
import vproxybase.util.nio.ByteArrayChannel;

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
