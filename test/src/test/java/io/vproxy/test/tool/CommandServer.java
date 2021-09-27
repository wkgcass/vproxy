package io.vproxy.test.tool;

import io.vproxy.base.connection.*;
import vproxy.base.connection.*;
import io.vproxy.base.selector.TimerEvent;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.util.LinkedList;

public class CommandServer implements ServerHandler {
    public static final String CMD_FIN = "fin";
    public static final String CMD_DELAY = "delay";
    public static final String CMD_CONSUME = "consume:";
    public static final int DELAY_TIMEOUT = 2_000;

    @Override
    public void acceptFail(ServerHandlerContext ctx, IOException err) {
        Logger.error(LogType.SOCKET_ERROR, "failed to accept new connection", err);
    }

    @Override
    public void connection(ServerHandlerContext ctx, Connection connection) {
        var handler = new ConnectionHandler() {
            final LinkedList<ByteArrayChannel> toSend = new LinkedList<>();
            final LinkedList<ByteArrayChannel> delaySend = new LinkedList<>();
            boolean finReceived = false;
            boolean finSent = false;
            boolean delayReply = false;
            TimerEvent delayTimer = null;

            @Override
            public void readable(ConnectionHandlerContext ctx) {
                var inBuf = ctx.connection.getInBuffer();
                ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(inBuf.used());
                inBuf.writeTo(chnl);

                var original = new String(chnl.getBytes());
                var commands = original.split("\n");
                for (var command : commands) {
                    if (command.isBlank()) {
                        continue;
                    }
                    command = command.trim();
                    Logger.alert("received command: " + command);

                    if (command.equals(CMD_FIN)) {
                        finSent = true;
                        ctx.connection.closeWrite();
                        Logger.warn(LogType.ALERT, "FIN sent");
                    } else if (command.equals(CMD_DELAY)) {
                        delayReply = true;
                        Logger.warn(LogType.ALERT, "delay reply enabled");
                    } else if (command.startsWith(CMD_CONSUME)) {
                        Logger.warn(LogType.ALERT, "consumed");
                    } else {
                        // run echo as default
                        if (finSent) {
                            Logger.error(LogType.ALERT, "FIN already sent, should not echo");
                            return;
                        }
                        if (delayReply) {
                            delaySend.add(ByteArrayChannel.fromFull((command + "\r\n").getBytes()));
                            if (delayTimer == null) {
                                delayTimer = ctx.eventLoop.getSelectorEventLoop().delay(DELAY_TIMEOUT, () -> {
                                    delayTimer = null;
                                    delayReply = false;
                                    Logger.warn(LogType.ALERT, "delay reply disabled");
                                    toSend.addAll(delaySend);
                                    delaySend.clear();
                                    write(ctx);
                                });
                            }
                        } else {
                            toSend.add(ByteArrayChannel.fromFull((command + "\r\n").getBytes()));
                            write(ctx);
                        }
                    }
                }
            }

            @Override
            public void writable(ConnectionHandlerContext ctx) {
                write(ctx);
                checkAndCloseConnection(ctx);
            }

            private void write(ConnectionHandlerContext ctx) {
                var outBuf = ctx.connection.getOutBuffer();
                while (!toSend.isEmpty()) {
                    var first = toSend.peekFirst();
                    if (first.used() == 0) {
                        toSend.pollFirst();
                        continue;
                    }
                    int n = outBuf.storeBytesFrom(first);
                    if (n == 0) { // nothing sent
                        break;
                    }
                }
                checkAndCloseConnection(ctx);
            }

            private void checkAndCloseConnection(ConnectionHandlerContext ctx) {
                if (finReceived && toSend.isEmpty() && delaySend.isEmpty()) {
                    if (ctx.connection.getOutBuffer().used() == 0) {
                        ctx.connection.close();
                    }
                }
            }

            @Override
            public void exception(ConnectionHandlerContext ctx, IOException err) {
                Logger.error(LogType.ALERT, connection + " got exception", err);
            }

            @Override
            public void remoteClosed(ConnectionHandlerContext ctx) {
                Logger.alert(connection + " received FIN");
                finReceived = true;
                checkAndCloseConnection(ctx);
            }

            @Override
            public void closed(ConnectionHandlerContext ctx) {
                Logger.alert(connection + " closed");
            }

            @Override
            public void removed(ConnectionHandlerContext ctx) {
                Logger.alert(connection + " removed from loop");
            }
        };
        try {
            ctx.eventLoop.addConnection(connection, null, handler);
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "failed to initiate connection " + connection, e);
            connection.close();
        }
    }

    @Override
    public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
        return new Tuple<>(RingBuffer.allocate(24576), RingBuffer.allocate(24576));
    }

    @Override
    public void removed(ServerHandlerContext ctx) {
        Logger.alert(this + " removed from loop");
    }
}
