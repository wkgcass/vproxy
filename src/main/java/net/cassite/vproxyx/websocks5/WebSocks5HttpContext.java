package net.cassite.vproxyx.websocks5;

import net.cassite.vproxy.http.HttpContext;

public class WebSocks5HttpContext extends HttpContext {
    final WebSocks5ProxyContext webSocks5ProxyContext;

    public WebSocks5HttpContext(WebSocks5ProxyContext webSocks5ProxyContext) {
        this.webSocks5ProxyContext = webSocks5ProxyContext;
    }
}
