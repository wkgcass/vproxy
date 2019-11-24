package vproxy.socks;

import vproxy.connection.Connector;
import vproxy.protocol.ProtocolHandler;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.util.*;
import vproxy.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Socks5ProxyProtocolHandler implements ProtocolHandler<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> {
    private final Socks5ConnectorProvider connectorProvider;

    public Socks5ProxyProtocolHandler(Socks5ConnectorProvider connectorProvider) {
        this.connectorProvider = connectorProvider;
    }

    @Override
    public void init(ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("socks5 init " + ctx.connectionId);
        ctx.data = new Tuple<>(new Socks5ProxyContext(ctx.inBuffer), null);
    }

    @Override
    public void readable(ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("socks5 readable " + ctx.connectionId);

        Socks5ProxyContext pctx = ctx.data.left;

        // 3 10 -1 do not read, but writes
        loop:
        while (pctx.hasNext() || pctx.state == 3 || pctx.state == 10 || pctx.state == -1) {
            switch (pctx.state) {
                case 0:
                    pctx.state = version(pctx, 1);
                    break;
                case 1:
                    pctx.state = authMethodCount(pctx);
                    break;
                case 2:
                    pctx.state = methods(pctx);
                    break;
                case 3:
                    pctx.state = serverAuth(ctx, pctx);
                    break;
                case 4:
                    pctx.state = version(pctx, 5);
                    break;
                case 5:
                    pctx.state = command(pctx);
                    break;
                case 6:
                    pctx.state = preserved(pctx);
                    break;
                case 7:
                    pctx.state = reqType(pctx);
                    break;
                case 8:
                    pctx.state = address(pctx);
                    break;
                case 9:
                    pctx.state = port(pctx);
                    break;
                case 10:
                    pctx.state = serverDone(ctx, pctx);
                    break;
                case 11:
                    pctx.state = callback(ctx, pctx);
                    break;
                case 12:
                    // done
                    break loop;
                default:
                    fail(ctx, pctx);
                    break loop;
            }
        }
    }

    private static int version(Socks5ProxyContext pctx, int nextStep) {
        byte version = pctx.next();
        if (version != 5) {
            // socks 5 version should be 5
            return -1;
        } else {
            return nextStep;
        }
    }

    private static int authMethodCount(Socks5ProxyContext pctx) {
        byte bcount = pctx.next();
        int count = Utils.positive(bcount);
        if (count == 0) {
            // client support no methods
            return -1;
        }
        pctx.clientMethodLeft = count;
        pctx.clientSupportedMethods = new byte[count];
        return 2; // methods
    }

    private static int methods(Socks5ProxyContext pctx) {
        byte meth = pctx.next();
        pctx.clientSupportedMethods[pctx.clientSupportedMethods.length - pctx.clientMethodLeft] = meth;
        --pctx.clientMethodLeft;
        if (pctx.clientMethodLeft == 0) {
            return 3; // let server write message back
        } else {
            return 2; // still expecting methods
        }
    }

    private static int serverAuth(ProtocolHandlerContext
                                      <Tuple<Socks5ProxyContext, Callback<Connector, IOException>>>
                                      ctx,
                                  Socks5ProxyContext pctx) {
        boolean found = false;
        for (byte meth : pctx.clientSupportedMethods) {
            if (meth == 0) {
                // no auth
                found = true;
                break;
            }
        }
        if (!found) {
            return -1; // no supported methods
        }
        byte[] writeBack = new byte[]{5/*version=5*/, 0/*meth=0*/};
        ctx.write(writeBack);
        pctx.isDoingAuth = false;
        return 4; // version
    }

    private static int command(Socks5ProxyContext pctx) {
        byte cmd = pctx.next();
        if (cmd != 0x01) {
            pctx.errType = Socks5ProxyContext.COMMAND_NOT_SUPPORTED;
            return -1;
        }
        pctx.clientCommand = cmd;
        return 6; // preserved
    }

    private static int preserved(Socks5ProxyContext pctx) {
        pctx.next(); // ignore the field
        return 7; // req type
    }

    private static int reqType(Socks5ProxyContext pctx) {
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
            pctx.errType = Socks5ProxyContext.ADDRESS_TYPE_NOT_SUPPORTED;
            return -1;
        }
        return 8; // address
    }

    private static int address(Socks5ProxyContext pctx) {
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
                pctx.errType = Socks5ProxyContext.ADDRESS_TYPE_NOT_SUPPORTED;
                return -1;
            }

            return 8; // still address
        }

        // handle address data
        byte b = pctx.next();
        pctx.address[pctx.address.length - pctx.addressLeft] = b;
        --pctx.addressLeft;
        if (pctx.addressLeft == 0) {
            return 9; // expecting port
        } else {
            return 8; // still address
        }
    }

    private static int port(Socks5ProxyContext pctx) {
        byte b = pctx.next();
        pctx.portBytes[pctx.portBytes.length - pctx.portLeft] = b;
        --pctx.portLeft;
        if (pctx.portLeft == 0) {
            // calculate port
            pctx.port = ((pctx.portBytes[0] << 8) & 0xFFFF) | (pctx.portBytes[1] & 0xFF);
            return 10; // serverDone
        } else {
            return 9; // still port
        }
    }

    private static byte[] getCommonResp(Socks5ProxyContext pctx) {
        return new byte[]{
            5 /*version*/,
            pctx.errType /*resp*/,
            0 /*preserved*/,
            1 /*type: ipv4*/,
            0, 0, 0, 0 /*0.0.0.0*/,
            (byte) ((pctx.port >> 8) & (0xff)) /*port1*/,
            (byte) ((pctx.port) & (0xff)) /*port2*/
        };
    }

    private /*static*/ int serverDone(ProtocolHandlerContext
                                          <Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx,
                                      Socks5ProxyContext pctx) {
        if (pctx.hasNext()) {
            Logger.warn(LogType.UNEXPECTED, "still got data after receiving socks 5 `port`");
            pctx.errType = Socks5ProxyContext.GENERAL_SOCKS_SERVER_FAILURE;
            return -1;
        }
        // address
        String address;
        if (pctx.reqType == AddressType.domain) {
            address = new String(pctx.address, StandardCharsets.UTF_8);
        } else {
            try {
                //noinspection ResultOfMethodCallIgnored
                InetAddress.getByAddress(pctx.address);
            } catch (UnknownHostException e) {
                Logger.warn(LogType.INVALID_EXTERNAL_DATA, "the input address " + Arrays.toString(pctx.address));
                pctx.errType = Socks5ProxyContext.GENERAL_SOCKS_SERVER_FAILURE;
                return -1;
            }
            address = Utils.ipStr(pctx.address);
        }
        boolean[] sameThread = {false}; // tell the outside method, that callback and method are running in the same thread
        boolean[] returned = {false}; // tell the callback, that the outside method has already returned
        //noinspection UnnecessaryLocalVariable
        Object lock = sameThread; // randomly pick an object as the lock
        connectorProvider.provide(ctx.connection, pctx.reqType, address, pctx.port, connector -> {
            // we should do a lock
            // the `sameThread` and `returned` state should be synchronized
            synchronized (lock) {

                // setting the `sameThread` field to true
                // it will be checked before method `serverDone()` returns
                // if it's already returned, this flag would be false before returning
                // otherwise, it will be true which indicates that the callback and
                // `serverDone()` are in the same thread
                sameThread[0] = true;

                if (connector == null) {
                    assert Logger.lowLevelDebug("connector NOT found for " + address + ":" + pctx.port);
                    pctx.errType = Socks5ProxyContext.CONNECTION_NOT_ALLOWED_BY_RULESET;
                    pctx.state = -1;
                    if (returned[0]) {
                        // the method already returned, so it should invoke `fall` by itself
                        // instead of invoked by the while-loop code
                        fail(ctx, pctx);
                    }
                } else {
                    assert Logger.lowLevelDebug("connector found for " + address + ":" + pctx.port + ", " + connector);
                    if (pctx.state == -1) {
                        assert Logger.lowLevelDebug("but the handler context is invalid now: " + pctx);
                        // close it
                        connector.close();
                        return;
                    }
                    pctx.connector = connector;
                    pctx.errType = 0;
                    byte[] writeBack = getCommonResp(pctx);
                    ctx.write(writeBack);
                    pctx.state = 11; // change state to (callback)
                    // and then, wait for client data
                }
            }
        });
        synchronized (lock) {
            returned[0] = true;
            if (sameThread[0]) {
                return pctx.state; // return the state assigned in callback
            } else {
                return 12; // just break the loop for now, data and further events will be handled in callback
            }
        }
    }

    private static int callback(ProtocolHandlerContext
                                    <Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx,
                                Socks5ProxyContext pctx) {
        assert Logger.lowLevelDebug("callback called on " + pctx);
        pctx.done = true; // mark it's done
        ctx.data.right.succeeded(pctx.connector);
        return 12; // done
    }

    private static void fail(ProtocolHandlerContext
                                 <Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx,
                             Socks5ProxyContext pctx) {
        assert Logger.lowLevelDebug("socks5 failed " + ctx.connectionId + " state = " + pctx.state + " err = " + pctx.errType);
        // clear buffer
        if (pctx.inBuffer.used() > 0) {
            byte[] x = new byte[pctx.inBuffer.used()];
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(x);
            while (pctx.inBuffer.used() > 0) {
                chnl.reset();
                pctx.inBuffer.writeTo(chnl);
            }
        }

        if (pctx.isDoingAuth) {
            ctx.write(new byte[]{5, (byte) 0xFF});
        } else {
            if (pctx.errType == 0) {
                pctx.errType = 1;
            }
            ctx.write(getCommonResp(pctx));
        }
    }

    @Override
    public void exception(ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx, Throwable err) {
        // connection should be closed by the protocol lib
        // we ignore the exception here
        assert Logger.lowLevelDebug("socks5 exception " + ctx.connectionId + ", " + err);
        checkAndCloseConnector(ctx);
    }

    @Override
    public void end(ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        // connection is closed by the protocol lib
        // we ignore the event here
        assert Logger.lowLevelDebug("socks5 end " + ctx.connectionId);
        checkAndCloseConnector(ctx);
    }

    private void checkAndCloseConnector(ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("checkAndCloseConnector: " + ctx.data);

        if (closeOnRemoval(ctx)
            && ctx.data != null
            && ctx.data.left != null) {

            Socks5ProxyContext pctx = ctx.data.left;
            assert Logger.lowLevelDebug("socks5 proxy protocol handler ended or got exception, so we close connector " + pctx.connector);
            pctx.state = -1;
            pctx.errType = Socks5ProxyContext.GENERAL_SOCKS_SERVER_FAILURE;

            if (ctx.data.left.connector != null) {
                // should close the retrieved connector if it's not returned to outside
                pctx.connector.close();
            }
        }
    }

    @Override
    public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("closeOnRemoval called on pctx " + ctx.data);
        if (ctx.data == null || ctx.data.left == null) {
            // return true when it's not fully initialized
            return true;
        }
        // otherwise check whether it's done
        return !ctx.data.left.done;
    }
}
