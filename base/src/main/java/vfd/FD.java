package vfd;

import vproxybase.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.Channel;

public interface FD extends Channel {
    void configureBlocking(boolean b) throws IOException;

    <T> void setOption(SocketOption<T> name, T value) throws IOException;

    /**
     * @return the real fd object
     */
    FD real();

    default void loopAware(SelectorEventLoop loop) {
    }
}
