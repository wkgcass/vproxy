package vproxy.http;

import vproxy.util.AbstractParser;
import vproxy.util.Utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class HttpReqParser extends AbstractParser<HttpReq> {
    private final boolean parseBody;

    public HttpReqParser(boolean parseBody) {
        super(new HashSet<>(Arrays.asList(9, 100)), Collections.singleton(100));
        result = new HttpReq();
        this.parseBody = parseBody;
    }

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
     * 16: \n before body, expecting body for content-length -> 16 then -> 9
     */

    protected int doSwitch(byte b) {
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
        result.method = new StringBuilder();
        result.method.append(c);
        return 1; // httpMethod
    }

    private int state01httpMethod(char c) {
        if (c == ' ') {
            return 2; // space before url
        } else {
            result.method.append(c);
            return 1; // not changed
        }
    }

    private int state02spaceBeforeUrl(char c) {
        if (c == ' ') {
            return 2; // not changed
        } else {
            result.url = new StringBuilder();
            result.url.append(c);
            return 3; // url
        }
    }

    private int state03url(char c) {
        if (c == ' ') {
            return 4; // space after url
        } else {
            result.url.append(c);
            return 3; // not changed
        }
    }

    private int state04spaceAfterUrl(char c) {
        if (c == ' ') {
            return 4; // not changed
        } else {
            result.version = new StringBuilder();
            result.version.append(c);
            return 5; // version
        }
    }

    private int state05version(char c) {
        if (c == '\r') {
            return 6; // \r after version
        } else {
            result.version.append(c);
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
            result.currentParsingHeader = new HttpHeader();
            result.currentParsingHeader.key = new StringBuilder();
            result.currentParsingHeader.key.append(c);
            return 10; // header key
        }
    }

    protected int state08returnBeforeEnd(char c) {
        if (c == '\n') {
            return 9; // \n before end
        } else {
            errorMessage = "state08: expecting \\n but got " + c;
            return -1; // error
        }
    }

    protected int state09newlineBeforeEnd() {
        errorMessage = "the request should have ended but still receiving data. pipeline not supported";
        return -1; // error
    }

    protected int state10headerKey(char c) {
        if (c == ':') {
            return 11; // header colon
        } else {
            result.currentParsingHeader.key.append(c);
            return 10;
        }
    }

    protected int state11headerColon(char c) {
        if (c == ' ') {
            return 11; // not changed
        } else {
            result.currentParsingHeader.value = new StringBuilder();
            result.currentParsingHeader.value.append(c);
            return 12; // header value
        }
    }

    protected int state12headerValue(char c) {
        if (c == '\r') {
            return 13; // \r after header
        } else {
            result.currentParsingHeader.value.append(c);
            return 12; // not changed
        }
    }

    protected int state13returnAfterHeader(char c) {
        if (c == '\n') {
            return 14; // \n after header
        } else {
            errorMessage = "state13: expecting \\n but got " + c;
            return -1; // error
        }
    }

    protected int state14newlineAfterHeader(char c) {
        // record the header
        result.headers.add(result.currentParsingHeader);
        result.currentParsingHeader = null;
        if (c == '\r') {
            return 15; // \r after last header
        } else {
            result.currentParsingHeader = new HttpHeader();
            result.currentParsingHeader.key = new StringBuilder();
            result.currentParsingHeader.key.append(c);
            return 10; // header key
        }
    }

    @SuppressWarnings("Duplicates") // same as HttpRespParser
    protected int state15returnAfterLastHeader(char c) {
        if (c == '\n') {
            // check content-length
            for (HttpHeader h : result.headers) {
                if (h.key.toString().trim().equalsIgnoreCase("content-length")) {
                    try {
                        result.bodyLen = Integer.parseInt(h.value.toString().trim());
                    } catch (NumberFormatException e) {
                        errorMessage = "invalid Content-Length: " + h.value.toString().trim();
                        return -1;
                    }
                    break;
                }
            }
            if (result.bodyLen < 0) {
                errorMessage = "invalid Content-Length: " + result.bodyLen + " < 0";
                return -1;
            } else if (result.bodyLen == 0) {
                return 9; // \n before end
            } else {
                if (parseBody) {
                    result.body = new StringBuilder();
                    return 16; // \n before body
                } else {
                    return 100; // end before body
                }
            }
        } else {
            errorMessage = "state15: expecting \\n but got " + c;
            return -1; // error
        }
    }

    @SuppressWarnings("Duplicates") // same as HttpRespParser
    protected int state16newlineBeforeBody(char c) {
        if (result.bodyLen == 0) {
            return state09newlineBeforeEnd(); // let it raise error
        } else {
            result.body.append(c);
            --result.bodyLen;
            if (result.bodyLen == 0) {
                return 9; // let it end
            }
            return 16; // body, not changed
        }
    }
}
