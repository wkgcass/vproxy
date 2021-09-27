package vproxy.base.redis;

import vproxy.base.redis.entity.*;
import vproxy.base.util.Logger;
import vproxy.base.util.RingBuffer;
import vproxy.base.util.Utils;
import vproxy.base.util.nio.ByteArrayChannel;

@SuppressWarnings("Duplicates")
public class RESPParser {
    private final int maxLen;
    private int parsedLen = 0;

    private RESP resp = null;
    private String errorMessage = null;
    private int state = 0; // 0 is the start state

    /*
     * state machine:
     *
     * 0 ---> the initial state
     * ----->(+)---> simple string,create RESPString ---> 1
     * ----->(-)---> simple error, create RESPError ----> 3
     * ----->(:)---> integer, create RESPInteger -------> 4
     * ----->($)---> bulk string, create RESPBulkString-> 5
     * ----->(*)---> array, create RESPArray -----------> 9
     * ----->(otherwise)-> inline, createRESPInline add append ----> 13
     * 1 ---> simple string
     * ----->(\r)--> simple end -----> 2
     * ----->(otherwise)--> append --> 1
     * 2 ---> simple end
     * ----->(\n)--> END
     * 3 ---> simple error
     * ----->(\r)--> simple end -----> 2
     * ----->(otherwise)--> append --> 3
     * 4 ---> integer
     * ----->(-)---> set negative ---> 15
     * ----->(digital)--> increase --> 16
     * 5 ---> bulk string
     * ----->(-) -------> set negative -> 14
     * ----->(digital)--> increase len -> 6
     * 6 ---> bulk string len
     * ----->(\r) -> if (len==-1) --> 2,else len<0 error, otherwise create stringBuilder -> 7
     * ----->(digital)--> increase len -> 6
     * 7 ---> bulk string header end
     * ----->(\n) -> bulk string body --> 8
     * 8 ---> bulk string body
     * ----->for $len times, read data--> 8
     * then->(\r) -> simple end -----> 2
     * 9 ---> array
     * ----->(digital)--> increase --> 10
     * 10---> array len
     * ----->(\r) -> array header end->11
     * ----->(digital)--> increase --> 10
     * 11---> array header end
     * ----->(\n)->if arr.len == 0 end, otherwise array body-> 12
     * 12---> array body
     * -----> start a new parser for $len times -->12
     * then-> end
     * 13---> inline
     * ----->(\r) -> simple end -----> 2
     * ----->(otherwise)-> append ---> 13
     *
     * // here parses the bulk string
     * 14---> bulk string len digital
     * ----->(digital)--> increase len -> 6
     *
     * // here parses the integer
     * 15---> integer digital
     * ----->(digital)--> increase -----> 16
     * 16---> integer may end
     * ----->(digital)--> increase -----> 16
     * ----->(\r) ------> simple end ---> 2
     */

    public RESPParser(int maxLen) {
        this.maxLen = maxLen;
    }

    // return 0 means everything is done
    // return -1 means: got error, or want more data
    // call getErrorMessage() to check whether is error
    public int feed(RingBuffer buffer) {
        byte[] nextByte = Utils.allocateByteArrayInitZero(1);
        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(nextByte);
        while (true) {
            chnl.reset();
            // an integer field that does multiple things, no particular name for it
            int foo = buffer.writeTo(chnl);
            if (foo == 0)
                return -1; // indicating `want more`
            ++parsedLen;
            if (parsedLen > maxLen) {
                errorMessage = "too many input bytes";
                return -1;
            }
            foo = doSwitch(Utils.positive(nextByte[0]));
            if (foo == -1 || foo == 0)
                return foo;
        }
    }

    // only for private switch methods
    private static final int GOT_ERROR = -1;
    private static final int DONE = 0;
    private static final int WANT_MORE = 1;

    private int error(String msg) {
        assert Logger.lowLevelDebug(Utils.stackTraceStartingFromThisMethodInclusive()[1].getLineNumber() + " - " + msg);
        this.errorMessage = msg;
        return GOT_ERROR;
    }

    // return GOT_ERROR when error
    // return DONE when done
    // return WANT_MORE when want to continue
    private int doSwitch(int b) {
        int res;
        switch (state) {
            case 0:
                res = switchInitialState0(b);
                break;
            case 1:
                res = switchSimpleString1(b);
                break;
            case 2:
                res = switchSimpleEnd2(b);
                break;
            case 3:
                res = switchSimpleError3(b);
                break;
            case 4:
                res = switchInteger4(b);
                break;
            case 5:
                res = switchBulkString5(b);
                break;
            case 6:
                res = switchBulkStringLen6(b);
                break;
            case 7:
                res = switchBulkStringHeaderEnd7(b);
                break;
            case 8:
                res = switchBulkStringBody8(b);
                break;
            case 9:
                res = switchArray9(b);
                break;
            case 10:
                res = switchArrayLen10(b);
                break;
            case 11:
                res = switchArrayHeaderEnd11(b);
                break;
            case 12:
                res = switchArrayBody12(b);
                break;
            case 13:
                res = switchInline13(b);
                break;
            case 14:
                res = switchBulkStringLenDigital14(b);
                break;
            case 15:
                res = switchIntegerDigital15(b);
                break;
            case 16:
                res = switchIntegerMayEnd16(b);
                break;
            default:
                Logger.shouldNotHappen("bug in the state machine impl, state = " + state);
                throw new Error("bug in the state machine impl, state = " + state);
        }
        if (res < 0 || res == 0)
            return res;
        state = res;
        return WANT_MORE;
    }

    // for the following switch methods:
    // return GOT_ERROR when error
    // return DONE when done
    // return other value as the state to change to

    private int switchInitialState0(int b) {
        switch (b) {
            case '+':
                resp = new RESPString();
                return 1;
            case '-':
                resp = new RESPError();
                return 3;
            case ':':
                resp = new RESPInteger();
                return 4;
            case '$':
                resp = new RESPBulkString();
                return 5;
            case '*':
                resp = new RESPArray();
                return 9;
            default:
                resp = new RESPInline();
                ((RESPInline) resp).string.append((char) b);
                return 13;
        }
    }

    private int switchSimpleString1(int b) {
        switch (b) {
            case '\r':
                return 2;
            default:
                ((RESPString) resp).string.append((char) b);
                return 1;
        }
    }

    private int switchSimpleEnd2(int b) {
        switch (b) {
            case '\n':
                return DONE;
            default:
                return error("expecting \\n");
        }
    }

    private int switchSimpleError3(int b) {
        switch (b) {
            case '\r':
                return 2;
            default:
                ((RESPError) resp).error.append((char) b);
                return 3;
        }
    }

    private int switchInteger4(int b) {
        if (b == '-') {
            ((RESPInteger) resp).negative = -1;
            return 15;
        }
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPInteger integer = (RESPInteger) resp;
            integer.integer = 10 * integer.integer + d * integer.negative;
            return 16;
        }
        return error("expecting digital or -");
    }

    private int switchIntegerDigital15(int b) {
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPInteger integer = (RESPInteger) resp;
            integer.integer = 10 * integer.integer + d * integer.negative;
            return 16;
        }
        return error("expecting digital");
    }

    private int switchIntegerMayEnd16(int b) {
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPInteger integer = (RESPInteger) resp;
            integer.integer = 10 * integer.integer + d * integer.negative;
            return 16;
        }
        if (b == '\r') {
            return 2;
        }
        return error("expecting digital or \\r");
    }

    private int switchBulkString5(int b) {
        if (b == '-') {
            ((RESPBulkString) resp).negative = -1;
            return 14;
        }
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPBulkString bs = (RESPBulkString) resp;
            bs.len = bs.len * 10 + d * bs.negative;
            return 6;
        }
        return error("expecting digital or -");
    }

    private int switchBulkStringLenDigital14(int b) {
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPBulkString bs = (RESPBulkString) resp;
            bs.len = bs.len * 10 + d * bs.negative;
            return 6;
        }
        return error("expecting digital");
    }

    private int switchBulkStringLen6(int b) {
        if (b == '\r') {
            RESPBulkString bs = (RESPBulkString) resp;
            if (bs.len == -1) {
                return 2;
            } else if (bs.len < 0) {
                // -2,-3 etc
                // invalid
                return error("bulk string length cannot be " + bs.len);
            } else {
                bs.string = new StringBuilder();
                return 7;
            }
        }
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPBulkString bs = (RESPBulkString) resp;
            bs.len = bs.len * 10 + d * bs.negative;
            return 6;
        }
        return error("expecting digital or \\r");
    }

    private int switchBulkStringHeaderEnd7(int b) {
        if (b == '\n') {
            return 8;
        }
        return error("expecting \\n");
    }

    private int switchBulkStringBody8(int b) {
        RESPBulkString bs = (RESPBulkString) resp;
        if (bs.len == 0) {
            if (b == '\r') {
                return 2;
            }
            return error("expecting \\r");
        } else {
            --bs.len;
            bs.string.append((char) b);
            return 8;
        }
    }

    private int switchArray9(int b) {
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPArray array = (RESPArray) resp;
            array.len = array.len * 10 + d;
            return 10;
        }
        return error("expecting digital");
    }

    private int switchArrayLen10(int b) {
        if (b == '\r') {
            return 11;
        }
        if (b >= '0' && b <= '9') {
            int d = b - '0';
            RESPArray array = (RESPArray) resp;
            array.len = array.len * 10 + d;
            return 10;
        }
        return error("expecting digital or \\r");
    }

    private int switchArrayHeaderEnd11(int b) {
        if (b == '\n') {
            RESPArray array = (RESPArray) resp;
            if (array.len == 0)
                return DONE;
            return 12;
        }
        return error("expecting \\n");
    }

    private int switchArrayBody12(int b) {
        RESPArray array = (RESPArray) resp;
        if (array.parser == null) {
            array.parser = new RESPParser(this.maxLen - parsedLen);
        }
        int res = array.parser.doSwitch(b);
        if (res == -1)
            return error(array.parser.errorMessage);
        if (res == 0) {
            array.array.add(array.parser.getResult());
            array.parser = null;
            --array.len;
            if (array.len == 0) {
                return DONE;
            }
        }
        return 12;
    }

    private int switchInline13(int b) {
        if (b == '\r') {
            return 2;
        } else {
            ((RESPInline) resp).string.append((char) b);
            return 13;
        }
    }

    public RESP getResult() {
        return resp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
