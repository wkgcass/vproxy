package net.cassite.vproxy.http;

import net.cassite.vproxy.util.AbstractParser;
import net.cassite.vproxy.util.Utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class HttpRespParser extends AbstractParser<HttpResp> {
    private final boolean parseBody;

    public HttpRespParser(boolean parseBody) {
        super(new HashSet<>(Arrays.asList(9, 100)), Collections.singleton(100));
        this.parseBody = parseBody;
    }

    /*
     * state:
     * 0: init, expecting version -> 1
     * 1: version, expecting space -> 2
     * 2: space after version, expecting space -> 2 or status code -> 3
     * 3: status code, expecting space -> 4 or status code -> 3
     * 4: space after status code, expecting space -> 4 or status message -> 5
     * 5: status message, expecting \r -> 6 or status message -> 5
     * 6: \r after status message, expecting \n -> 7
     * 7: \n after status message, expecting \r -> 8 or header key -> 10
     * 8: \r before end, expecting \n -> 9
     * 9: \n before end, _END_
     * 10: header key, expecting `:` -> 11 or header key -> 10
     * 11: colon of header, expecting space -> 11 or header value -> 12
     * 12: header value, expecting \r -> 13 or header value -> 12
     * 13: \r after header, expecting \n -> 14
     * 14: \n after header, expecting \r -> 15 or header key -> 10
     * 15: \r after last header, if no content-length or content-length is 0, expecting \r -> 8, otherwise expecting \n -> 16
     * 16: \n before body, expecting body for content-length -> 16 then -> 9
     */

    @Override
    protected int doSwitch(byte b) {
        char c = (char) Utils.positive(b);
        switch (state) {
            case 0:
                return state00init(c);
            case 1:
                return state01version(c);
            case 2:
                return state02spaceAfterVersion(c);
            case 3:
                return state03statusCode(c);
            case 4:
                return state04spaceAfterStatusCode(c);
            case 5:
                return state05statusMessage(c);
            case 6:
                return state06returnAfterStatusMessage(c);
            case 7:
                return state07newlineAfterStatusMessage(c);
            case 8:
                return state08returnBeforeEnd(c);
            case 9:
                return state09newlineBeforeEnd();
            case 10:
                return state10headerKey(c);
            case 11:
                return state11colonOfHeader(c);
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
                // -1 means fail
                // simply return
                return -1;
            default:
                errorMessage = "unknown state";
                return -1;
        }
    }

    private int state00init(char c) {
        result.version = new StringBuilder();
        result.version.append(c);
        return 1; // version
    }

    private int state01version(char c) {
        if (c == ' ') {
            return 2; // space after version
        } else {
            result.version.append(c);
            return 1; // not changed
        }
    }

    private int state02spaceAfterVersion(char c) {
        if (c == ' ') {
            return 2; // not changed
        } else {
            result.statusCode = new StringBuilder();
            result.statusCode.append(c);
            return 3; // status code
        }
    }

    private int state03statusCode(char c) {
        if (c == ' ') {
            return 4; // space after status code
        } else {
            result.statusCode.append(c);
            return 3; // not changed
        }
    }

    private int state04spaceAfterStatusCode(char c) {
        if (c == ' ') {
            return 4; // not changed
        } else {
            result.statusMessage = new StringBuilder();
            result.statusMessage.append(c);
            return 5; // status message
        }
    }

    private int state05statusMessage(char c) {
        if (c == '\r') {
            return 6; // \r after status message
        } else {
            result.statusMessage.append(c);
            return 5; // not changed
        }
    }

    private int state06returnAfterStatusMessage(char c) {
        if (c == '\n') {
            return 7; // \n after status message
        } else {
            errorMessage = "state06: expecting \\n but got " + c;
            return -1; // error
        }
    }

    private int state07newlineAfterStatusMessage(char c) {
        if (c == '\r') {
            return 8; // \r before end
        } else {
            result.currentHeader = new HttpHeader();
            result.currentHeader.key = new StringBuilder();
            result.currentHeader.key.append(c);
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
            return 11; // colon of header
        } else {
            result.currentHeader.key.append(c);
            return 10; // not changed
        }
    }

    private int state11colonOfHeader(char c) {
        if (c == ' ') {
            return 11; // not changed
        } else {
            result.currentHeader.value = new StringBuilder();
            result.currentHeader.value.append(c);
            return 12; // header value
        }
    }

    private int state12headerValue(char c) {
        if (c == '\r') {
            return 13; // \r after header
        } else {
            result.currentHeader.value.append(c);
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
        result.headers.add(result.currentHeader);
        result.currentHeader = null;
        if (c == '\r') {
            return 15; // \r after last header
        } else {
            result.currentHeader = new HttpHeader();
            result.currentHeader.key = new StringBuilder();
            result.currentHeader.key.append(c);
            return 10; // header key
        }
    }

    @SuppressWarnings("Duplicates") // same as HttpReqParser
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

    @SuppressWarnings("Duplicates") // same as HttpReqParser
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
