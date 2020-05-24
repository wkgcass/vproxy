package vproxyx.websocks.ss;

import vfd.IP;
import vproxy.socks.AddressType;
import vproxy.socks.Socks5ConnectorProvider;
import vproxybase.connection.Connector;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.util.Callback;
import vproxybase.util.Logger;
import vproxybase.util.Tuple;
import vproxybase.util.Utils;
import vproxybase.util.crypto.Aes256Key;
import vproxybase.util.ringbuffer.ByteBufferRingBuffer;
import vproxybase.util.ringbuffer.DecryptIVInDataUnwrapRingBuffer;
import vproxybase.util.ringbuffer.EncryptIVInDataWrapRingBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SSProtocolHandler implements ProtocolHandler<Tuple<SSContext, Callback<Connector, IOException>>> {
    private final String password;
    private final Socks5ConnectorProvider connectorProvider;

    public SSProtocolHandler(String password, Socks5ConnectorProvider connectorProvider) {
        this.password = password;
        this.connectorProvider = connectorProvider;
    }

    @Override
    public void init(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx) {
        initCipher(ctx);
        ctx.data = new Tuple<>(new SSContext(ctx.inBuffer), null);
    }

    private void initCipher(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx) {
        var in = ctx.connection.getInBuffer();
        var out = ctx.connection.getOutBuffer();
        Aes256Key key = new Aes256Key(password);
        try {
            // when init, there should have not read any data yet
            // so we should safely replace the buffers
            ctx.connection.UNSAFE_replaceBuffer(
                new DecryptIVInDataUnwrapRingBuffer((ByteBufferRingBuffer) in, key, ctx.connection.channel),
                new EncryptIVInDataWrapRingBuffer((ByteBufferRingBuffer) out, key, ctx.connection.channel));
        } catch (IOException e) {
            Logger.shouldNotHappen("got error when switching buffers", e);
            // raise error to let others handle the error
            throw new RuntimeException("should not happen and the error is unrecoverable", e);
        }
    }

    @Override
    public void readable(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("ss readable " + ctx.connectionId);

        SSContext pctx = ctx.data.left;

        // 3 10 -1 do not read, but writes
        loop:
        while (pctx.hasNext() || pctx.state == -1 || pctx.state == 3 || pctx.state == 4) {
            switch (pctx.state) {
                case 0:
                    pctx.state = reqType(pctx);
                    break;
                case 1:
                    pctx.state = address(pctx);
                    break;
                case 2:
                    pctx.state = port(pctx);
                    break;
                case 3:
                    pctx.state = callback(ctx, pctx);
                    break;
                case 4:
                    // done
                    break loop;
                default:
                    fail(ctx);
                    break loop;
            }
        }
    }

    private void fail(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("close the connection because handling failed");
        ctx.connection.close(); // close the connection when failed
    }

    private static int reqType(SSContext pctx) {
        byte reqType = pctx.next();
        if (reqType == 0x01) {
            assert Logger.lowLevelDebug("ipv4 req");
            pctx.reqType = AddressType.ipv4;
        } else if (reqType == 0x03) {
            assert Logger.lowLevelDebug("domain req");
            pctx.reqType = AddressType.domain;
        } else if (reqType == 0x04) {
            assert Logger.lowLevelDebug("ipv6 req");
            pctx.reqType = AddressType.ipv6;
        } else {
            assert Logger.lowLevelDebug("unknown reqType " + reqType);
            return -1;
        }
        return 1; // address
    }

    private static int address(SSContext pctx) {
        if (pctx.address == null) {
            if (pctx.reqType == AddressType.ipv4) {
                // ipv4
                pctx.address = new byte[4];
                pctx.addressLeft = 4;
            } else if (pctx.reqType == AddressType.domain) {
                byte blen = pctx.next(); // read first byte as the length of domain string
                int len = Utils.positive(blen);
                pctx.address = new byte[len];
                pctx.addressLeft = len;
            } else if (pctx.reqType == AddressType.ipv6) {
                // ipv6
                pctx.address = new byte[16];
                pctx.addressLeft = 16;
            } else {
                assert Logger.lowLevelDebug("invalid reqType " + pctx.reqType);
                return -1;
            }

            return 1; // still address
        }

        // handle address data
        byte b = pctx.next();
        pctx.address[pctx.address.length - pctx.addressLeft] = b;
        --pctx.addressLeft;
        if (pctx.addressLeft == 0) {
            return 2; // expecting port
        } else {
            return 1; // still address
        }
    }

    private static int port(SSContext pctx) {
        byte b = pctx.next();
        pctx.portBytes[pctx.portBytes.length - pctx.portLeft] = b;
        --pctx.portLeft;
        if (pctx.portLeft == 0) {
            // calculate port
            pctx.port = ((pctx.portBytes[0] << 8) & 0xFFFF) | (pctx.portBytes[1] & 0xFF);
            return 3; // done
        } else {
            return 2; // still port
        }
    }

    private int callback(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx, SSContext pctx) {
        // address
        String address;
        if (pctx.reqType == AddressType.domain) {
            address = new String(pctx.address, StandardCharsets.UTF_8);
        } else {
            address = IP.ipStr(pctx.address);
        }
        connectorProvider.provide(ctx.connection, pctx.reqType, address, pctx.port, connector -> {
            if (connector == null) {
                assert Logger.lowLevelDebug("connector NOT found for " + address + ":" + pctx.port);
                fail(ctx);
            } else {
                assert Logger.lowLevelDebug("connector found for " + address + ":" + pctx.port + ", " + connector);
                ctx.data.right.succeeded(connector);
            }
        });

        return 4; // done
    }

    @Override
    public void exception(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx, Throwable err) {
        // connection should be closed by the protocol lib
        // we ignore the exception here
        assert Logger.lowLevelDebug("ss exception " + ctx.connectionId + ", " + err);
    }

    @Override
    public void end(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx) {
        // connection is closed by the protocol lib
        // we ignore the event here
        assert Logger.lowLevelDebug("ss end " + ctx.connectionId);
    }

    @Override
    public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<SSContext, Callback<Connector, IOException>>> ctx) {
        int step = ctx.data.left.state;
        return step < 4;
    }
}
