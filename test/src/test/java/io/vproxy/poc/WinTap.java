package io.vproxy.poc;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.Event;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.windows.WindowsFDs;

import java.io.IOException;

public class WinTap {
    public static void main(String[] args) throws IOException {
        var windowsFDs = new WindowsFDs();
        var fd = windowsFDs.openTap("tap0");

        var buf = Utils.allocateByteBuffer(2048);

        var selector = windowsFDs.openSelector();
        selector.register(fd, EventSet.read(), null);

        while (true) {
            var entries = selector.select();
            Logger.alert("iocp wakeup with " + entries.size() + " entries");
            for (var entry : entries) {
                if (entry.fd() != fd) {
                    continue;
                }
                if (!entry.ready().have(Event.READABLE)) {
                    continue;
                }
                buf.limit(buf.capacity()).position(0);
                int n = fd.read(buf);
                if (n == 0) {
                    continue;
                }
                Logger.alert("read " + n + " bytes");

                buf.flip();
                buf.put(0, (byte) 0xAA);
                buf.put(1, (byte) 0xBB);
                buf.put(2, (byte) 0xCC);

                n = fd.write(buf);
                Logger.alert("wrote " + n + " bytes");
            }
        }
    }
}
