package io.vproxy.adaptor.netty.channel;

import io.netty.util.internal.ThrowableUtil;

import java.nio.channels.ClosedChannelException;

public final class StacklessClosedChannelException extends ClosedChannelException {
    private StacklessClosedChannelException() {
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    public static StacklessClosedChannelException newInstance(Class<?> clazz, String method) {
        return ThrowableUtil.unknownStackTrace(new StacklessClosedChannelException(), clazz, method);
    }
}
