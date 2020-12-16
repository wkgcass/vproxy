package vproxybase.socks;

import vfd.IPPort;
import vfd.IPv4;
import vproxybase.connection.Connection;
import vproxybase.util.Callback;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.Utils;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.IOException;

public class Socks5ClientHandshake {
    private final Connection connection;
    private final Callback<Void, IOException> cb;
    private final AddressType addressType;
    private final String targetDomain;
    private final int targetDomainPort;
    private final IPPort target;

    private int step = 0;
    // 0: initial, send auth_method_exchange
    // 1: socks5 auth method respond
    // 2: send socks5 connect
    // 3: socks5 response (first 5 bytes)
    // 4: read socks5 response (left)
    // 5: done
    private final ByteArrayChannel socks5AuthMethodExchange = ByteArrayChannel.fromEmpty(2);
    private final ByteArrayChannel socks5ConnectResultFirst5Bytes = ByteArrayChannel.fromEmpty(5);
    private ByteArrayChannel socks5ConnectResultLeft = null;

    public Socks5ClientHandshake(Connection connection,
                                 String targetDomain, int port,
                                 Callback<Void, IOException> cb) {
        this.connection = connection;
        this.addressType = AddressType.domain;
        this.targetDomain = targetDomain;
        this.targetDomainPort = port;
        this.target = null;
        this.cb = cb;
    }

    public Socks5ClientHandshake(Connection connection,
                                 IPPort target,
                                 Callback<Void, IOException> cb) {
        this.connection = connection;
        this.addressType = target.getAddress() instanceof IPv4 ? AddressType.ipv4 : AddressType.ipv6;
        this.targetDomain = null;
        this.targetDomainPort = 0;
        this.target = target;
        this.cb = cb;
    }

    public boolean isDone() {
        return cb.isCalled();
    }

    private boolean isHandling = false;

    public void trigger() {
        if (isHandling) {
            return;
        }
        isHandling = true;
        trigger0();
        isHandling = false;
    }

    private void trigger0() {
        if (isDone()) {
            assert Logger.lowLevelDebug("the socks5 handshaking is already done, read nothing");
            return;
        }
        loop:
        while (true) {
            switch (step) {
                case -1:
                    return;
                case 0:
                    sendSocks5AuthMethodExchange();
                    step = 1;
                    break loop;
                case 1:
                    connection.getInBuffer().writeTo(socks5AuthMethodExchange);
                    if (socks5AuthMethodExchange.free() != 0) {
                        return; // not fully read, waiting for more data
                    }
                    step = checkAndProcessAuthExchangeAndSendConnect();
                    break;
                case 2:
                    sendSocks5Connect();
                    step = 3;
                    break loop;
                case 3:
                    connection.getInBuffer().writeTo(socks5ConnectResultFirst5Bytes);
                    if (socks5ConnectResultFirst5Bytes.free() != 0) {
                        return; // not fully read, waiting for more data
                    }
                    step = checkAndProcessFirst5BytesOfConnectResult();
                    break;
                case 4:
                    connection.getInBuffer().writeTo(socks5ConnectResultLeft);
                    if (socks5ConnectResultLeft.free() != 0) {
                        return;
                    }
                    step = 5;
                    // fall through
                case 5:
                    cb.succeeded();
                    break loop;
            }
        }
    }

    private void sendSocks5AuthMethodExchange() {
        byte[] toSend = {
            5, // version
            1, // cound
            0, // no auth
        };
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(toSend);
        connection.getOutBuffer().storeBytesFrom(chnl);
    }

    private int checkAndProcessAuthExchangeAndSendConnect() {
        byte[] ex = socks5AuthMethodExchange.getBytes();
        if (ex[0] != 5 || ex[1] != 0) {
            // version != 5 or meth != no_auth
            String errorMsg = "response version is wrong or method is wrong: " + ex[0] + "," + ex[1];
            Logger.error(LogType.INVALID_EXTERNAL_DATA, errorMsg);
            cb.failed(new IOException(errorMsg));
            return -1;
        }
        return 2;
    }

    private void sendSocks5Connect() {
        // build message to send
        byte[] domainBytes = addressType == AddressType.domain ? targetDomain.getBytes() : null;
        int len;
        switch (addressType) {
            case ipv4:
                len = 1 + 1 + 1 + 1 + 4 + 2;
                break;
            case domain:
                len = 1 + 1 + 1 + 1 + (1 + domainBytes.length) + 2;
                break;
            case ipv6:
                len = 1 + 1 + 1 + 1 + 16 + 2;
                break;
            default:
                Logger.shouldNotHappen("should not reach here, unsupported addressType: " + addressType);
                throw new IllegalArgumentException("" + addressType);
        }
        byte[] toSend = new byte[len];
        toSend[0] = 5; // version
        toSend[1] = 1; // connect
        toSend[2] = 0; // preserved
        toSend[3] = addressType.code; // type
        //---
        int port = addressType == AddressType.domain ? targetDomainPort : target.getPort();
        toSend[toSend.length - 2] = (byte) ((port >> 8) & 0xff);
        toSend[toSend.length - 1] = (byte) (port & 0xff);

        switch (addressType) {
            case ipv4:
                System.arraycopy(target.getAddress().getAddress(), 0, toSend, 4, 4);
                break;
            case domain:
                toSend[4] = (byte) domainBytes.length; // domain length
                System.arraycopy(domainBytes, 0, toSend, 5, domainBytes.length);
                break;
            case ipv6:
                System.arraycopy(target.getAddress().getAddress(), 0, toSend, 4, 16);
                break;
            default:
                Logger.shouldNotHappen("should not reach here, unsupported addressType: " + addressType);
                throw new IllegalArgumentException("" + addressType);
        }

        ByteArrayChannel chnl = ByteArrayChannel.fromFull(toSend);
        connection.getOutBuffer().storeBytesFrom(chnl);
    }

    private int checkAndProcessFirst5BytesOfConnectResult() {
        byte[] connect5Bytes = socks5ConnectResultFirst5Bytes.getBytes();
        if (connect5Bytes[0] != 5 || connect5Bytes[1] != 0) {
            // version != 5 or resp != success
            String errMsg = "response version is wrong or resp is not success: " + connect5Bytes[0] + "," + connect5Bytes[1] +
                ". handling " + targetDomain + ":" + targetDomainPort + " | " + target;
            Logger.error(LogType.INVALID_EXTERNAL_DATA, errMsg);
            cb.failed(new IOException(errMsg));
            return -1;
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
                String errMsg = "RESP_TYPE is invalid: " + connect5Bytes[3];
                Logger.error(LogType.INVALID_EXTERNAL_DATA, errMsg);
                cb.failed(new IOException(errMsg));
                return -1;
        }

        // check the input buffer, whether already contain the left data
        if (connection.getInBuffer().used() == leftLen) {
            connection.getInBuffer().clear();
            return 5;
        } else {
            socks5ConnectResultLeft = ByteArrayChannel.fromEmpty(leftLen);
            // consume the bytes left
            connection.getInBuffer().writeTo(socks5ConnectResultLeft);
            if (socks5ConnectResultLeft.free() > 0) {
                // read more data
                return 4;
            } else {
                return 5;
            }
        }
    }
}
