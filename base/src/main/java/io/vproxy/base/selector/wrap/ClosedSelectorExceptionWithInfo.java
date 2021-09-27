package io.vproxy.base.selector.wrap;

import java.nio.channels.ClosedSelectorException;

public class ClosedSelectorExceptionWithInfo extends ClosedSelectorException {
    private final String msg;

    public ClosedSelectorExceptionWithInfo(String msg) {
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
