package vproxyx.websocks;

import vproxy.http.HttpContext;

public class WebSocksHttpContext extends HttpContext {
    final WebSocksProxyContext webSocksProxyContext;

    public WebSocksHttpContext(WebSocksProxyContext webSocksProxyContext) {
        this.webSocksProxyContext = webSocksProxyContext;
    }
}
