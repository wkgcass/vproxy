package io.vproxy.base.http;

import io.vproxy.base.processor.http1.builder.HttpEntityBuilder;
import io.vproxy.base.processor.http1.builder.ResponseBuilder;
import io.vproxy.base.processor.http1.entity.Response;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.codec.AbstractParser;

public class HttpRespParser extends AbstractParser<Response> {
    /**
     * @see HttpParserHelper#handlers
     */
    private final HttpParserHelper.Handler[] handlers = new HttpParserHelper.Handler[]{
        this::state0, // 0 entry
        null, // 1 req
        null, // 2 req
        null, // 3 req
        null, // 4 helper
        null, // 5 helper
        null, // 6 helper
        null, // 7 helper
        null, // 8 helper
        null, // 9 helper
        null, // 10 helper
        null, // 11 helper
        null, // 12 helper
        null, // 13 helper
        null, // 14 helper
        null, // 15 helper
        null, // 16 helper
        null, // 17 helper
        null, // 18 helper
        null, // 19 helper
        null, // 20 helper
        null, // 21 helper
        this::state22,
        this::state23,
        this::state24,
    };
    private final HttpParserHelper helper;
    private ResponseBuilder resp;
    private boolean parseAll;

    public HttpRespParser(boolean parseAll) {
        super(parseAll ? HttpParserHelper.terminateStatesParseAllMode : HttpParserHelper.terminateStatesStepsMode);
        this.parseAll = parseAll;
        this.helper = new HttpParserHelper(parseAll) {
            @Override
            int getState() {
                return state;
            }

            @Override
            void setState(int state) {
                HttpRespParser.this.state = state;
            }

            @Override
            HttpEntityBuilder getHttpEntity() {
                return resp;
            }
        };
    }

    private void nextState() {
        try {
            state = helper.nextState(state);
        } catch (Exception e) {
            Logger.shouldNotHappen("nextState(" + state + ")", e);
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    protected int doSwitch(byte b) {
        if (state < 0 || state >= helper.handlers.length) {
            throw new IllegalStateException("BUG: unexpected state " + state);
        }

        if (!parseAll) {
            if (HttpParserHelper.hasNextState.contains(state)) {
                nextState();
            }
        }

        try {
            if (state == 0 || (22 <= state && state <= 24)) {
                return handlers[state].handle(b);
            } else {
                return helper.doSwitch(b);
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return -1;
        }
    }

    private int state0(byte b) {
        resp = new ResponseBuilder();
        return state22(b);
    }

    private int state22(byte b) {
        if (b == ' ') {
            return 23;
        } else {
            resp.version.append((char) b);
            return 22;
        }
    }

    private int state23(byte b) throws Exception {
        if (b == ' ') {
            return 24;
        } else {
            if (b < '0' || b > '9') {
                throw new Exception("invalid character in http response status code: " + ((char) b));
            }
            resp.statusCode.append((char) b);
            return 23;
        }
    }

    private int state24(byte b) {
        if (b == '\r') {
            // ignore
            return 24;
        } else if (b == '\n') {
            return 4;
        } else {
            resp.reason.append((char) b);
            return 24;
        }
    }
}
