package net.cassite.vproxyx.websocks5;

import net.cassite.vproxy.http.HttpHeader;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;

import java.util.Base64;
import java.util.List;
import java.util.function.Predicate;

public class WebSocks5Utils {
    /*
      0                   1                   2                   3
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-------+-+-------------+-------------------------------+
     |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
     |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
     |N|V|V|V|       |S|             |   (if payload len==126/127)   |
     | |1|2|3|       |K|             |                               |
     +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
     |     Extended payload length continued, if payload len == 127  |
     + - - - - - - - - - - - - - - - +-------------------------------+
     |                               |Masking-key, if MASK set to 1  |
     +-------------------------------+-------------------------------+
     | Masking-key (continued)       |          Payload Data         |
     +-------------------------------- - - - - - - - - - - - - - - - +
     :                     Payload Data continued ...                :
     + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
     |                     Payload Data continued ...                |
     +---------------------------------------------------------------+
     */
    // we want:
    // 1. FIN set
    // 2. opcode = %x2 denotes a binary frame
    // 3. mask unset
    // 4. payload_len = 2^63-1
    // payload is ignored
    public static final byte[] bytesToSendForWebSocketFrame = {
        (byte) (128 | 2), // FIN,0,0,0,0,0,1,0
        127, // enable extended payload len (64)
        127, // first byte, 01111111
        -1, // second byte, all 1
        -1, // third byte, all 1
        -1, // fourth byte, all 1
        -1, // fifth byte, all 1
        -1, // sixth byte, all 1
        -1, // seventh byte, all 1
        -1, // eighth byte, all 1
        // which makes 2^63-1
        // no masking-key
        // then payload continues
    };

    private WebSocks5Utils() {
    }

    public static void sendWebSocketFrame(RingBuffer outBuffer) {
        //noinspection ConstantConditions,TrivialFunctionalExpressionUsage,AssertWithSideEffects
        assert ((Predicate<Void>) v -> {
            // for debug purpose
            // we set the 3rd to 10th byte to 0....3
            // (3 for socks5 first 3 bytes sent by client)
            // in this way, the wireshark will be able to print WebSocket frame
            // if set to 2^63-1, the wireshark will not be able to make a too large buffer
            // and will result in an error:
            //
            // in packet-websocket.c, the wireshark code writes:
            // tvb_payload = tvb_new_subset_length_caplen(tvb, payload_offset, payload_length, payload_length);
            //
            // if too big, it will result in memory allocation failure
            //
            // we only use this for debug purpose,
            // to show that this is part of valid WebSocket protocol
            //
            // in real world, we use 2^63-1

            bytesToSendForWebSocketFrame[2] = 0;
            bytesToSendForWebSocketFrame[3] = 0;
            bytesToSendForWebSocketFrame[4] = 0;
            bytesToSendForWebSocketFrame[5] = 0;
            bytesToSendForWebSocketFrame[6] = 0;
            bytesToSendForWebSocketFrame[7] = 0;
            bytesToSendForWebSocketFrame[8] = 0;
            bytesToSendForWebSocketFrame[9] = 3;
            return true;
        }).test(null);

        ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytesToSendForWebSocketFrame);
        outBuffer.storeBytesFrom(chnl);
    }

    // we check the Upgrade, Connection and Sec-Websocket-Accept or Sec-WebSocket-Key
    // other are ignored
    // if isClient = true, then will check for client, which means
    // check `xxx-Accept` header
    // otherwise will check `xxx-Key` header
    //
    // return true if pass, false if fail
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean checkUpgradeToWebSocketHeaders(List<HttpHeader> headers, boolean isClient) {
        boolean foundUpgrade = false;
        boolean foundSec = false;
        boolean foundConnection = false;
        for (HttpHeader header : headers) {
            String headerKey = header.key.toString().trim();
            String headerVal = header.value.toString().trim();
            if (headerKey.equalsIgnoreCase("upgrade")) {
                if (headerVal.equals("websocket")) {
                    foundUpgrade = true;
                    if (foundSec && foundConnection) {
                        break;
                    }
                } else {
                    // invalid
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "server invalid header Upgrade: " + headerVal);
                    return false;
                }
            } else if (headerKey.equalsIgnoreCase(
                isClient ? "sec-websocket-accept" : "sec-websocket-key"
            )) {
                boolean pass;
                if (isClient) {
                    // the client uses the same key for each connection
                    // so the result should be the same as well
                    // copied from rfc 6455
                    pass = headerVal.equals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
                } else {
                    // the server should check the base64 string
                    try {
                        pass = Base64.getDecoder().decode(headerVal).length == 16;
                    } catch (IllegalArgumentException e) {
                        pass = false;
                    }
                }
                if (pass) {
                    foundSec = true;
                    if (foundUpgrade && foundConnection) {
                        break;
                    }
                } else {
                    // invalid
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "server invalid header " + headerKey + ": " + headerVal);
                    return false;
                }
            } else if (headerKey.equalsIgnoreCase("connection")) {
                if (headerVal.equals("Upgrade")) {
                    foundConnection = true;
                    if (foundSec && foundUpgrade) {
                        break;
                    }
                } else {
                    // invalid
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "server invalid header Connection: " + headerVal);
                    return false;
                }
            }
        }

        if (!foundUpgrade || !foundSec || !foundConnection) {
            // invalid resp
            Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                "server invalid response" +
                    ": foundUpgrade=" + foundUpgrade +
                    ", foundSec=" + foundSec +
                    ", foundConnection=" + foundConnection);
            return false;
        }
        return true;
    }
}
