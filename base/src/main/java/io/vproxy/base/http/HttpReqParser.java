package io.vproxy.base.http;

import io.vproxy.base.processor.http1.builder.HttpEntityBuilder;
import io.vproxy.base.processor.http1.builder.RequestBuilder;
import io.vproxy.base.processor.http1.entity.Request;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.codec.AbstractParser;

public class HttpReqParser extends AbstractParser<Request> {
    /**
     * @see HttpParserHelper#handlers
     */
    private final HttpParserHelper.Handler[] handlers = new HttpParserHelper.Handler[]{
        this::state0,
        this::state1,
        this::state2,
        this::state3,
    };
    private final HttpParserHelper helper;
    private RequestBuilder req;
    private final boolean parseAll;

    public HttpReqParser(boolean parseAll) {
        super(parseAll ? HttpParserHelper.terminateStatesParseAllMode : HttpParserHelper.terminateStatesStepsMode);
        this.parseAll = parseAll;
        this.helper = new HttpParserHelper(parseAll) {
            @Override
            int getState() {
                return state;
            }

            @Override
            void setState(int state) {
                HttpReqParser.this.state = state;
            }

            @Override
            HttpEntityBuilder getHttpEntity() {
                return req;
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
            if (state <= 3) {
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
        req = new RequestBuilder();
        return state1(b);
    }

    private int state1(byte b) {
        if (b == ' ') {
            return 2;
        } else {
            req.method.append((char) b);
            return 1;
        }
    }

    private int state2(byte b) {
        if (b == ' ') {
            return 3;
        } else if (b == '\r') {
            // do nothing
            return 2;
        } else if (b == '\n') {
            return 4;
        } else {
            req.uri.append((char) b);
            return 2;
        }
    }

    private int state3(byte b) {
        if (b == '\r') {
            // do nothing
            return 3;
        } else if (b == '\n') {
            return 4;
        } else {
            if (req.version == null) {
                req.version = new StringBuilder();
            }
            req.version.append((char) b);
            return 3;
        }
    }
}
