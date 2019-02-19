package net.cassite.vproxyx.websocks5;

import net.cassite.vproxy.connection.ConnectionHandlerContext;
import net.cassite.vproxy.http.HttpHeader;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;

import java.util.List;

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

    public static void sendWebSocketFrame(ConnectionHandlerContext ctx) {
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytesToSendForWebSocketFrame);
        ctx.connection.outBuffer.storeBytesFrom(chnl);
    }

    // we check the Upgrade, Connection and Sec-Websocket-Accept or Sec-WebSocket-Key
    // other are ignored
    // if isClient = true, then will check for client, which means
    // check `xxx-Accept` header
    // otherwise will check `xxx-Key` header
    //
    // return true if pass, false if fail
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
                        "server invalid response header Upgrade: " + headerVal);
                    return false;
                }
            } else if (headerKey.equalsIgnoreCase(
                isClient ? "sec-websocket-accept" : "sec-websocket-key"
            )) {
                if (headerVal.equals(
                    // copied from rfc 6455
                    isClient ? "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=" : "dGhlIHNhbXBsZSBub25jZQ=="
                )) {
                    foundSec = true;
                    if (foundUpgrade && foundConnection) {
                        break;
                    }
                } else {
                    // invalid
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "server invalid response header " + headerKey + ": " + headerVal);
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
                        "server invalid response header Connection: " + headerVal);
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
