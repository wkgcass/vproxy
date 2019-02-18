package net.cassite.vproxy.http;

import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;

public class HttpParser {
    private final HttpReq req = new HttpReq();
    private int state = 0;
    private String errorMessage;

    private final byte[] bytes = new byte[1];
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);

    /*
     * state:
     * 0: init, expecting http method -> 1
     * 1: http method, expecting http method -> 1 or space -> 2
     * 2: space before url, expecting space -> 2 or url -> 3
     * 3: url, expecting url -> 3 or space -> 4
     * 4: space after url, expecting space -> 4 or version -> 5
     * 5: version, expecting version -> 5 or \r -> 6
     * 6: \r after version, expecting \n -> 7
     * 7: \n after version, expecting \r -> 8 or header key -> 10
     * 8: \r before end, expecting \n -> 9
     * 9: \n before end, __END__
     * 10: header key, expecting header key -> 10, or : -> 11
     * 11: header colon, expecting header value -> 12
     * 12: header value, expecting header value -> 12 or \r -> 13
     * 13: \r after header, expecting \n -> 14
     * 14: \n after header, expecting \r -> 15 or header key -> 10
     * 15: \r after last header, if no content-length or content-length 0, expecting \n -> 9,
     *                           otherwise expecting \n -> 16
     * 16: \n before body, expecting body for content-length -> 16 then \r -> 8
     */

    // -1 for need more or fail
    // 0 for done
    public int feed(RingBuffer buffer) {
        while (buffer.used() != 0) {
            chnl.reset();
            try {
                buffer.writeTo(chnl);
            } catch (IOException e) {
                // should not happen, it's memory operation
                return -1;
            }

            byte b = bytes[0];
            state = doSwitch(b);
            if (state == -1) { // parse failed, return -1
                return -1;
            }
        }
        if (state == 9) {
            return 0;
        }
        return -1; // indicating that the parser want more data
    }

    private int doSwitch(byte b) {
        char c = (char) Utils.positive(b);
        switch (state) {
            case 0:
                return state00init(c);
            case 1:
                return state01httpMethod(c);
            case 2:
                return state02spaceBeforeUrl(c);
            case 3:
                return state03url(c);
            case 4:
                return state04spaceAfterUrl(c);
            case 5:
                return state05version(c);
            case 6:
                return state06returnAfterVersion(c);
            case 7:
                return state07newlineAfterVersion(c);
            case 8:
                return state08returnBeforeEnd(c);
            case 9:
                return state09newlineBeforeEnd();
            case 10:
                return state10headerKey(c);
            case 11:
                return state11headerColon(c);
            case 12:
                return state12headerValue(c);
            case 13:
                return state13returnAfterHeader(c);
            case 14:
                return state14newlineAfterHeader(c);
            case 15:
                return state15returnAfterLastHeader(c);
            case 16:
                return state16newlineBeforeBody(c);
            case -1:
                // -1 means failed
                // simple return
                return -1;
            default:
                errorMessage = "unknown state";
                return -1;
        }
    }

    private int state00init(char c) {
        req.method = new StringBuilder();
        req.method.append(c);
        return 1; // httpMethod
    }

    private int state01httpMethod(char c) {
        if (c == ' ') {
            return 2; // space before url
        } else {
            req.method.append(c);
            return 1; // not changed
        }
    }

    private int state02spaceBeforeUrl(char c) {
        if (c == ' ') {
            return 2; // not changed
        } else {
            req.url = new StringBuilder();
            req.url.append(c);
            return 3; // url
        }
    }

    private int state03url(char c) {
        if (c == ' ') {
            return 4; // space after url
        } else {
            req.url.append(c);
            return 3; // not changed
        }
    }

    private int state04spaceAfterUrl(char c) {
        if (c == ' ') {
            return 4; // not changed
        } else {
            req.version = new StringBuilder();
            req.version.append(c);
            return 5; // version
        }
    }

    private int state05version(char c) {
        if (c == '\r') {
            return 6; // \r after version
        } else {
            req.version.append(c);
            return 5; // not changed
        }
    }

    private int state06returnAfterVersion(char c) {
        if (c == '\n') {
            return 7; // \n after version
        } else {
            errorMessage = "state06: expecting \\n but got " + c;
            return -1; // error
        }
    }

    private int state07newlineAfterVersion(char c) {
        if (c == '\r') {
            return 8; // \r before end
        } else {
            req.currentParsingHeader = new HttpHeader();
            req.currentParsingHeader.key = new StringBuilder();
            req.currentParsingHeader.key.append(c);
            return 10; // header key
        }
    }

    private int state08returnBeforeEnd(char c) {
        if (c == '\n') {
            return 9; // \n before end
        } else {
            errorMessage = "state08: expecting \\n but got " + c;
            return -1; // error
        }
    }

    private int state09newlineBeforeEnd() {
        errorMessage = "the request should have ended but still receiving data. pipeline not supported";
        return -1; // error
    }

    private int state10headerKey(char c) {
        if (c == ':') {
            return 11; // header colon
        } else {
            req.currentParsingHeader.key.append(c);
            return 10;
        }
    }

    private int state11headerColon(char c) {
        if (c == ' ') {
            return 11; // not changed
        } else {
            req.currentParsingHeader.value = new StringBuilder();
            req.currentParsingHeader.value.append(c);
            return 12; // header value
        }
    }

    private int state12headerValue(char c) {
        if (c == '\r') {
            return 13; // \r after header
        } else {
            req.currentParsingHeader.value.append(c);
            return 12; // not changed
        }
    }

    private int state13returnAfterHeader(char c) {
        if (c == '\n') {
            return 14; // \n after header
        } else {
            errorMessage = "state13: expecting \\n but got " + c;
            return -1; // error
        }
    }

    private int state14newlineAfterHeader(char c) {
        // record the header
        req.headers.add(req.currentParsingHeader);
        req.currentParsingHeader = null;
        if (c == '\r') {
            return 15; // \r after last header
        } else {
            req.currentParsingHeader = new HttpHeader();
            req.currentParsingHeader.key = new StringBuilder();
            req.currentParsingHeader.key.append(c);
            return 10; // header key
        }
    }

    private int state15returnAfterLastHeader(char c) {
        if (c == '\n') {
            // check content-length
            for (HttpHeader h : req.headers) {
                if (h.key.toString().trim().equalsIgnoreCase("content-length")) {
                    try {
                        req.bodyLen = Integer.parseInt(h.value.toString().trim());
                    } catch (NumberFormatException e) {
                        errorMessage = "invalid Content-Length: " + h.value.toString().trim();
                        return -1;
                    }
                    break;
                }
            }
            if (req.bodyLen < 0) {
                errorMessage = "invalid Content-Length: " + req.bodyLen + " < 0";
                return -1;
            } else if (req.bodyLen == 0) {
                return 9; // \n before end
            } else {
                req.body = new StringBuilder();
                return 16; // \n before body
            }
        } else {
            errorMessage = "state15: expecting \\n but got " + c;
            return -1; // error
        }
    }

    private int state16newlineBeforeBody(char c) {
        if (req.bodyLen == 0) {
            return state09newlineBeforeEnd(); // let it raise error
        } else {
            req.body.append(c);
            --req.bodyLen;
            if (req.bodyLen == 0) {
                return 9; // let it end
            }
            return 16; // body, not changed
        }
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public HttpReq getHttpReq() {
        return req;
    }
}
