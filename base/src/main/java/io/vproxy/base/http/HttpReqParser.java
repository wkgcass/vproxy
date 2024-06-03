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
    private final Params params;

    public HttpReqParser() {
        this(new Params());
    }

    public HttpReqParser(Params params) {
        super(params.segmentedParsing ? HttpParserHelper.terminateStatesStepsMode : HttpParserHelper.terminateStatesParseAllMode);
        this.params = new Params(params);
        this.helper = new HttpParserHelper(params) {
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

    public RequestBuilder getRequestBuilder() {
        return req;
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

        if (params.segmentedParsing) {
            if (HttpParserHelper.hasNextState.contains(state)) {
                nextState();
            }
        }

        int newState;
        try {
            if (state <= 3) {
                newState = handlers[state].handle(b);
            } else {
                newState = helper.doSwitch(b);
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return -1;
        }
        if (newState == 0) {
            if (params.buildResult) {
                result = req.build();
            }
        }
        return newState;
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

    public static class Params extends HttpParserHelper.Params {
        public Params() {
        }

        public Params(Params params) {
            super(params);
        }

        @Override
        public Params setSegmentedParsing(boolean segmentedParsing) {
            return (Params) super.setSegmentedParsing(segmentedParsing);
        }

        @Override
        public Params setBuildResult(boolean buildResult) {
            return (Params) super.setBuildResult(buildResult);
        }

        @Override
        public Params setHeadersOnly(boolean headersOnly) {
            return (Params) super.setHeadersOnly(headersOnly);
        }
    }
}
