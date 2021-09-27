package io.vproxy.vproxyx.websocks;

import io.vproxy.base.http.HttpContext;

public class WebSocksHttpContext extends HttpContext {
    final WebSocksProxyContext webSocksProxyContext;

    public WebSocksHttpContext(WebSocksProxyContext webSocksProxyContext) {
        this.webSocksProxyContext = webSocksProxyContext;
    }
}
