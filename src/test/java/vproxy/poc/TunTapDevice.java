package vproxy.poc;

import vfd.FDProvider;
import vfd.FDs;
import vfd.posix.PosixFDs;
import vfd.posix.TunTapDatagramFD;
import vproxy.util.ByteArray;
import vswitch.packet.EthernetPacket;

import java.nio.ByteBuffer;

public class TunTapDevice {
    public static void main(String[] args) throws Exception {
        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof PosixFDs)) {
            throw new Exception("unsupported");
        }
        PosixFDs posixFDs = (PosixFDs) fds;
        TunTapDatagramFD fd = posixFDs.openTunTap("tap%d", TunTapDatagramFD.IFF_TAP | TunTapDatagramFD.IFF_NO_PI);
        System.out.println("TunTap fd opened: " + fd);
        ByteBuffer buf = ByteBuffer.allocate(2048); // should always be enough for a network packet
        //noinspection InfiniteLoopStatement
        while (true) {
            buf.limit(buf.capacity()).position(0);
            int n = fd.read(buf);
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
