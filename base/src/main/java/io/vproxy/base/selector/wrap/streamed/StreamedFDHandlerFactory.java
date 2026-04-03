package io.vproxy.base.selector.wrap.streamed;

@FunctionalInterface
public interface StreamedFDHandlerFactory {
    StreamedFDHandler create(boolean client);
}
