package io.vproxy.adaptor.vertx;

import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;

public class VProxyLogDelegateFactory implements LogDelegateFactory {
    public static void register() {
        //noinspection deprecation
        System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, VProxyLogDelegateFactory.class.getName());
    }

    public VProxyLogDelegateFactory() {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LogDelegate createDelegate(String name) {
        return new VProxyLogDelegate(name);
    }
}
