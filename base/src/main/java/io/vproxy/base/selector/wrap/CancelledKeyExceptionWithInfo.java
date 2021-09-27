package io.vproxy.base.selector.wrap;

import java.nio.channels.CancelledKeyException;

public class CancelledKeyExceptionWithInfo extends CancelledKeyException {
    private final String msg;

    public CancelledKeyExceptionWithInfo(String msg) {
        this.msg = msg;
    }

    @Override
    public String getMessage() {
        return msg;
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }
}
