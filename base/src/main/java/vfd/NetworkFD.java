package vfd;

import vproxybase.util.Comment;

import java.io.IOException;

public interface NetworkFD<ADDR extends SockAddr> extends FD, ReadableByteStream, WritableByteStream {
    @Comment("maybe null when not connected, but must be filled when connected")
    ADDR getLocalAddress() throws IOException;

    @Comment("should not be null")
    ADDR getRemoteAddress() throws IOException;
}
