package vproxy.poc;

import vfd.FDProvider;
import vfd.FDs;
import vfd.posix.PosixFDs;
import vfd.posix.TapDatagramFD;
import vproxy.util.ByteArray;
import vswitch.packet.EthernetPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TapDevice {
    public static void main(String[] args) throws Exception {
        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof PosixFDs)) {
            throw new Exception("unsupported");
        }
        PosixFDs posixFDs = (PosixFDs) fds;
        TapDatagramFD fd = posixFDs.openTap("tap1");
        System.out.println("Tap fd opened: " + fd);
        System.out.println("Supports non-blocking: " + posixFDs.posix.tapNonBlockingSupported());
        ByteBuffer buf = ByteBuffer.allocate(2048); // should always be enough for a network packet
        //noinspection InfiniteLoopStatement
        while (true) {
            buf.limit(buf.capacity()).position(0);
            int n;
            try {
                n = fd.read(buf);
            } catch (IOException e) {
                if ("Input/output error".equals(e.getMessage())) {
                    Thread.sleep(1000);
                    continue;
                }
                throw e;
            }
            System.out.println("read " + n + " bytes from " + fd);
            ByteArray bytes = ByteArray.from(buf.array()).sub(0, n);
            EthernetPacket packet = new EthernetPacket();
            String err = packet.from(bytes);
            if (err != null) {
                System.out.println("got error when parsing the packet: " + err);
            } else {
                System.out.println(packet);
            }
        }
    }
}
