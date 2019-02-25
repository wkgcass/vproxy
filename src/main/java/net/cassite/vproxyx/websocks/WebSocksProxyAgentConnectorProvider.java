package net.cassite.vproxyx.websocks;

import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.SvrHandleConnector;
import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.http.HttpResp;
import net.cassite.vproxy.http.HttpRespParser;
import net.cassite.vproxy.socks.AddressType;
import net.cassite.vproxy.socks.Socks5ConnectorProvider;
import net.cassite.vproxy.util.*;
import net.cassite.vproxy.util.ringbuffer.SSLUtils;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class WebSocksProxyAgentConnectorProvider implements Socks5ConnectorProvider {
    private final List<Pattern> proxyDomains;
    private final ServerGroup servers;
    private final String user;
    private final String pass;

    public WebSocksProxyAgentConnectorProvider(List<Pattern> proxyDomains,
                                               ServerGroup servers,
                                               String user,
                                               String pass) {
        this.proxyDomains = proxyDomains;
        this.servers = servers;
        this.user = user;
        this.pass = pass;
    }

    private boolean needProxy(String address) {
        for (Pattern p : proxyDomains) {
            if (p.matcher(address).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback) {
        // check whether need to proxy to the WebSocks server
        if (type != AddressType.domain || !needProxy(address)) {
            // just directly connect to the endpoint
            Utils.directConnect(type, address, port, providedCallback);
            return;
        }

        // proxy the net flow using WebSocks

        NetEventLoop loop = accepted.getEventLoop();
        if (loop == null) {
            Logger.shouldNotHappen("the loop should be attached to the connection");
            providedCallback.accept(null);
            return;
        }
        // retrieve a remote connection
        SvrHandleConnector connector = servers.next();
        if (connector == null) {
            // no connectors for now
            // the process is definitely cannot proceed
            // we do not try direct connect here
            // (because it's specified in config file that this domain requires proxy)
            // just raise error
            providedCallback.accept(null);
            return;
        }
        ClientConnection conn;
        try {
            if ((Boolean) connector.getData() /*useSSL, see ConfigProcessor*/) {
                SSLEngine engine;
                if (connector.getHostName() == null) {
                    engine = WebSocksUtils.getSslContext().createSSLEngine();
                } else {
                    engine = WebSocksUtils.getSslContext().createSSLEngine(connector.getHostName(), connector.remote.getPort());
                }
                engine.setUseClientMode(true);
                SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(
                    engine,
                    RingBuffer.allocate(16384),
                    RingBuffer.allocate(16384),
                    32768,
                    32768,
                    loop.getSelectorEventLoop());
                conn = connector.connect(pair.left, pair.right);
            } else {
                conn = connector.connect(RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384));
            }
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "connect to " + connector + " failed", e);
            providedCallback.accept(null);
            return;
        }
        try {
            loop.addClientConnection(conn, null, new AgentClientConnectionHandler(
                connector.getHostName(), address, port,
                providedCallback, user, pass));
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add " + conn + " to loop failed", e);
            providedCallback.accept(null);
        }
    }
}

class AgentClientConnectionHandler implements ClientConnectionHandler {
    private final String domainOfProxy;
    private final String domain;
    private final int port;
    private final Consumer<Connector> providedCallback;

    private final String user;
    private final String pass;

    // 0: init,
    // 1: expecting http resp,
    // 2: expecting WebSocket resp,
    // 3: expecting socks5 auth method exchange
    // 4: preserved for socks5 auth result
    // 5: expecting socks5 connect result first 4 bytes
    // 6: expecting socks5 connect result
    private int step = 0;
    private HttpRespParser httpRespParser;
    private ByteArrayChannel webSocketFrame;
    private ByteArrayChannel socks5AuthMethodExchange;
    private ByteArrayChannel socks5ConnectResult;

    AgentClientConnectionHandler(String domainOfProxy,
                                 String domain,
                                 int port,
                                 Consumer<Connector> providedCallback,
                                 String user,
                                 String pass) {
        this.domainOfProxy = domainOfProxy;
        this.domain = domain;
        this.port = port;
        this.providedCallback = providedCallback;

        this.user = user;
        this.pass = pass;
    }

    @Override
    public void connected(ClientConnectionHandlerContext ctx) {
        // send http upgrade on connection
        byte[] bytes = ("" +
            "GET / HTTP/1.1\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Host: " + domainOfProxy + "\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" + // copied from rfc 6455, we don't care in the protocol
            "Sec-WebSocket-Version: 13\r\n" +
            "Sec-WebSocket-Protocol: socks5\r\n" + // for now, we support socks5 only
            "Authorization: Basic " +
            Base64.getEncoder().encodeToString((user + ":" + WebSocksUtils.calcPass(pass, Utils.currentMinute())).getBytes()) +
            "\r\n" +
            "\r\n"
        ).getBytes();
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytes);
        ctx.connection.getOutBuffer().storeBytesFrom(chnl);
        // the out-buffer is large enough to fit the message, so no need to buffer it from here

        step = 1;
        httpRespParser = new HttpRespParser(false);
    }

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        if (step == 1) {
            // http
            int res = httpRespParser.feed(ctx.connection.getInBuffer());
            if (res != 0) {
                String errMsg = httpRespParser.getErrorMessage();
                if (errMsg != null) {
                    // parse failed, server returned invalid message
                    providedCallback.accept(null); // return null
                    // close connection
                    ctx.connection.close();
                } // otherwise // want more data
                return;
            }
            // succeeded, let's examine the content
            checkAndProcessHttpRespAndSendWebSocketFrame(ctx, httpRespParser.getResult());
        } else if (step == 2) {
            // WebSocket
            ctx.connection.getInBuffer().writeTo(webSocketFrame);
            if (webSocketFrame.free() != 0) {
                return; // still have data to read
            }
            // read done, check whether still have data to read
            if (ctx.connection.getInBuffer().used() != 0) {
                // still got data
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "in buffer still have data other than the frame header " + ctx.connection.getInBuffer().toString());
                providedCallback.accept(null);
                ctx.connection.close();
                return;
            }
            // remove the chnl
            webSocketFrame = null;
            // start socks5 negotiation
            sendSocks5AuthMethodExchange(ctx);
        } else if (step == 3) {
            // socks5 auth method respond
            ctx.connection.getInBuffer().writeTo(socks5AuthMethodExchange);
            if (socks5AuthMethodExchange.free() != 0) {
                return; // still have data to read
            }
            // read done, check whether still have data to read
            if (ctx.connection.getInBuffer().used() != 0) {
                // still got data
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "in buffer still have data other than the auth exchange " + ctx.connection.getInBuffer().toString());
                providedCallback.accept(null);
                ctx.connection.close();
                return;
            }
            // process the resp
            checkAndProcessAuthExchangeAndSendConnect(ctx);
        } else if (step == 5) {
            // socks5 connected respond first 5 bytes
            ctx.connection.getInBuffer().writeTo(socks5ConnectResult);
            if (socks5ConnectResult.free() != 0) {
                return; // still have data to read
            }
            // process the resp
            checkAndProcessFirst5BytesOfConnectResult(ctx);
        } else {
            // left bytes for socks5
            ctx.connection.getInBuffer().writeTo(socks5ConnectResult);
            if (socks5ConnectResult.free() != 0) {
                return; // still have data to read
            }
            // check inBuffer
            if (ctx.connection.getInBuffer().used() != 0) {
                // still got data
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "in buffer still have data after socks5 connect response " + ctx.connection.getInBuffer().toString());
                providedCallback.accept(null);
                ctx.connection.close();
                return;
            }
            // process done
            done(ctx);
        }
    }

    private void checkAndProcessHttpRespAndSendWebSocketFrame(ConnectionHandlerContext ctx, HttpResp resp) {
        assert Logger.lowLevelDebug("got http response: " + resp);

        // remove the parser
        httpRespParser = null;

        // status code should be 101
        if (!resp.statusCode.toString().trim().equals("101")) {
            // the server refused to upgrade to WebSocket
            providedCallback.accept(null);
            ctx.connection.close();
            return;
        }
        // check headers
        if (!WebSocksUtils.checkUpgradeToWebSocketHeaders(resp.headers, true)) {
            providedCallback.accept(null);
            ctx.connection.close();
            return;
        }

        // clear the input buffer in case got body
        ctx.connection.getInBuffer().clear();

        // send WebSocket frame:
        WebSocksUtils.sendWebSocketFrame(ctx.connection.getOutBuffer());
        step = 2;
        // expecting to read the exactly same data as sent
        byte[] bytes = new byte[WebSocksUtils.bytesToSendForWebSocketFrame.length];
        webSocketFrame = ByteArrayChannel.fromEmpty(bytes);
    }

    private void sendSocks5AuthMethodExchange(ConnectionHandlerContext ctx) {
        byte[] toSend = {
            5, // version
            1, // cound
            0, // no auth
        };
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(toSend);
        ctx.connection.getOutBuffer().storeBytesFrom(chnl);
        step = 3;
        byte[] ex = new byte[2];
        socks5AuthMethodExchange = ByteArrayChannel.fromEmpty(ex);
    }

    private void checkAndProcessAuthExchangeAndSendConnect(ConnectionHandlerContext ctx) {
        byte[] ex = socks5AuthMethodExchange.get();
        if (ex[0] != 5 || ex[1] != 0) {
            // version != 5 or meth != no_auth
            Logger.error(LogType.INVALID_EXTERNAL_DATA,
                "response version is wrong or method is wrong: " + ex[0] + "," + ex[1]);
            providedCallback.accept(null);
            ctx.connection.close();
            return;
        }
        // set to null
        socks5AuthMethodExchange = null;

        // build message to send

        byte[] chars = domain.getBytes();

        int len = 1 + 1 + 1 + 1 + (1 + chars.length) + 2;
        byte[] toSend = new byte[len];
        toSend[0] = 5; // version
        toSend[1] = 1; // connect
        toSend[2] = 0; // preserved
        toSend[3] = 3; // domain
        toSend[4] = (byte) domain.length(); // domain length
        //---
        toSend[toSend.length - 2] = (byte) ((port >> 8) & 0xff);
        toSend[toSend.length - 1] = (byte) (port & 0xff);

        System.arraycopy(chars, 0, toSend, 5, chars.length);

        ByteArrayChannel chnl = ByteArrayChannel.fromFull(toSend);
        ctx.connection.getOutBuffer().storeBytesFrom(chnl);

        // make buffer for incoming data
        step = 5;
        byte[] connect5Bytes = new byte[5];
        // we only need 2 bytes to check whether connection established successfully
        // and read another THREE bytes to know how long this message is
        socks5ConnectResult = ByteArrayChannel.fromEmpty(connect5Bytes);
    }

    private void checkAndProcessFirst5BytesOfConnectResult(ConnectionHandlerContext ctx) {
        byte[] connect5Bytes = socks5ConnectResult.get();
        if (connect5Bytes[0] != 5 || connect5Bytes[1] != 0) {
            // version != 5 or resp != success
            Logger.error(LogType.INVALID_EXTERNAL_DATA,
                "response version is wrong or resp is not success: " + connect5Bytes[0] + "," + connect5Bytes[1]);
            providedCallback.accept(null);
            ctx.connection.close();
            return;
        }
        // [2] is preserved, ignore that
        // check [3] for type
        int leftLen;
        switch (connect5Bytes[3]) {
            case 1: // ipv4
                leftLen = 4 - 1 + 2;
                break;
            case 3: // domain
                leftLen = Utils.positive(connect5Bytes[4]) + 2;
                break;
            case 4: // ipv6
                leftLen = 16 - 1 + 2;
                break;
            default:
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "RESP_TYPE is invalid: " + connect5Bytes[3]);
                providedCallback.accept(null);
                ctx.connection.close();
                return;
        }

        // check the input buffer, whether already contain the left data
        if (ctx.connection.getInBuffer().used() == leftLen) {
            ctx.connection.getInBuffer().clear();
            done(ctx);
        } else if (ctx.connection.getInBuffer().used() < leftLen) {
            // read more data
            step = 6;
            byte[] foo = new byte[leftLen];
            socks5ConnectResult = ByteArrayChannel.fromEmpty(foo);
        } else {
            // more than leftLen, which is invalid
            Logger.error(LogType.INVALID_EXTERNAL_DATA,
                "still got data after the connection response" + ctx.connection.getInBuffer().toString());
            providedCallback.accept(null);
            ctx.connection.close();
        }
    }

    private void done(ConnectionHandlerContext ctx) {
        socks5ConnectResult = null;
        // remove the connection from loop
        ctx.eventLoop.removeConnection(ctx.connection);

        // add respond to the proxy lib

        InetAddress local;
        try {
            local = InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("getByName 0.0.0.0 failed", e);
            providedCallback.accept(null);
            ctx.connection.close();
            return;
        }
        providedCallback.accept(new AlreadyConnectedConnector(
            ctx.connection.remote, local, (ClientConnection) ctx.connection, ctx.eventLoop
        ));

        // every thing is done now
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        // do nothing here, the out buffer is large enough
    }

    @Override
    public void exception(ConnectionHandlerContext ctx, IOException err) {
        Logger.error(LogType.CONN_ERROR, "connection " + ctx.connection + " got exception", err);
        ctx.connection.close();
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("connection " + ctx.connection + " closed");
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("connection " + ctx.connection + " removed");
    }
}
