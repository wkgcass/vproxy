package net.cassite.vproxy.redis.application;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.redis.RESPParser;
import net.cassite.vproxy.redis.Serializer;
import net.cassite.vproxy.redis.entity.RESP;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class RESPClientUtils {
    private RESPClientUtils() {
    }

    public static void retry(NetEventLoop loop,
                             InetSocketAddress remote,
                             Object toSend,
                             int timeout,
                             int retryTimes,
                             Callback<Object, IOException> cb) {
        oneReq(loop, remote, toSend, timeout, new Callback<Object, IOException>() {
            @Override
            protected void onSucceeded(Object value) {
                cb.succeeded(value);
            }

            @Override
            protected void onFailed(IOException err) {
                if (retryTimes > 0) {
                    retry(loop, remote, toSend, timeout, retryTimes - 1, cb);
                } else {
                    cb.failed(err);
                }
            }
        });
    }

    public static void oneReq(NetEventLoop loop,
                              InetSocketAddress remote,
                              Object toSend,
                              int timeout,
                              Callback<Object, IOException> cb) {
        byte[] bytes = Serializer.from(toSend);
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytes);
        try { // catch IOException and call cb

            // use heap buffer here
            // because the connection will be terminated when gets data
            // connection won't last long
            ClientConnection conn = ClientConnection.create(remote, RingBuffer.allocate(16384), RingBuffer.allocate(16384));
            loop.addClientConnection(conn, null, new ClientConnectionHandler() {
                private final RESPParser parser = new RESPParser(16384);

                private void write(ConnectionHandlerContext ctx) {
                    ctx.connection.getOutBuffer().storeBytesFrom(chnl);
                }

                @Override
                public void connected(ClientConnectionHandlerContext ctx) {
                    // write data when connected
                    write(ctx);
                }

                @Override
                public void readable(ConnectionHandlerContext ctx) {
                    int res = parser.feed(ctx.connection.getInBuffer());
                    if (res == -1) {
                        String msg = parser.getErrorMessage();
                        if (msg == null) {
                            // want more data
                            return;
                        }
                        // error, close connection
                        ctx.connection.close();
                        cb.failed(new IOException("external data is not RESP format"));
                        return;
                    }
                    RESP resp = parser.getResult();
                    cb.succeeded(resp.getJavaObject());
                    ctx.connection.close();
                }

                @Override
                public void writable(ConnectionHandlerContext ctx) {
                    write(ctx);
                }

                @Override
                public void exception(ConnectionHandlerContext ctx, IOException err) {
                    cb.failed(err);
                    ctx.connection.close();
                }

                @Override
                public void closed(ConnectionHandlerContext ctx) {
                    // ignore
                }

                @Override
                public void removed(ConnectionHandlerContext ctx) {
                    ctx.connection.close();
                    if (!cb.isCalled()) {
                        cb.failed(new IOException("removed from event loop"));
                    }
                }
            });
            // add timeout event
            loop.getSelectorEventLoop().delay(timeout, () -> {
                if (!cb.isCalled()) {
                    cb.failed(new IOException("timeout"));
                    conn.close();
                }
            });
        } catch (IOException e) {
            cb.failed(e);
        }
    }
}
