package vfd;

import vproxybase.util.Comment;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface NetworkFD<ADDR extends SockAddr> extends FD, ReadableByteChannel, WritableByteChannel {
    @Comment("maybe null when not connected, but must be filled when connected")
    ADDR getLocalAddress() throws IOException;

    @Comment("should not be null")
    ADDR getRemoteAddress() throws IOException;
}
