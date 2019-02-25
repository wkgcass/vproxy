/**
 * This package implements a protocol called `websocks` that binds WebSocket and the SOCKS protocol together<br>
 * The protocol is simple and follows rfc specifications.<br>
 * 1. the client sends an http upgrade request to upgrade to WebSocket<br>
 * 2. the server responds with a 101 http response<br>
 * 3. the client sends a WebSocket frame with no mask and payload_len=pow(2,63)-1 and without any payload<br>
 * 4. the server sends a WebSocket frame with no mask and payload_len=pow(2,63)-1 and without any payload<br>
 * 5. the client follows socks5 handshake protocol<br>
 * 6. the server follows socks5 handshake protocol<br>
 * 7. when a proxy establishes, the connection goes on raw tcp<br>
 * For a practical use:
 * Let's assume we have a client called C,
 * two servers negotiating using this protocol called A and B,
 * and a remote server needs to be proxied to called S.<br>
 * B is deployed in a PaaS service behind an api gateway,
 * which only lets http and WebSocket net flow go through.<br>
 * In this situation, we can use this protocol to proxy plain tcp data<br>
 * 1. A starts a socks5 server<br>
 * 2. C connects to A (tcp connection establishes now, will run socks5 later)<br>
 * 3. A connects to B (http + half WebSocket frame)<br>
 * 4. C sends socks5 to A, A proxies that data to B<br>
 * 5. B connects to S (tcp) and tells A, A proxies that response to C<br>
 * 6. C sends/reads data to/from A, A proxies data to B, B proxies data to S<br>
 * You can also build a security link between A and B, using nginx/haproxy:<br>
 * C &lt;--socks5--&gt; A &lt;--websocks--&gt; WrapTLS &lt;--tls--&gt; UnwrapTLS &lt;--websocks--&gt; B &lt;--tcp--&gt; S<br>
 */
package net.cassite.vproxyx.websocks;