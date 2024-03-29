package io.vproxy.poc;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.TapDatagramFD;
import io.vproxy.vfd.windows.WindowsFDs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class WinTap {
    public static void main(String[] args) throws IOException {
        Utils.loadDynamicLibrary("vfdwindows");
        WindowsFDs windowsFDs = new WindowsFDs();
        TapDatagramFD fd = windowsFDs.openTap("tap0");

        ByteBuffer rcvBuf = Utils.allocateByteBuffer(2048);
        ByteBuffer sndBuf = Utils.allocateByteBuffer(2048);
        LinkedList<ByteArray> q = new LinkedList<>();
        VProxyThread.create(() -> {
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                int n;
                try {
                    n = fd.read(rcvBuf);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
                System.out.println("read " + n + " bytes");
                byte[] b = Utils.allocateByteArray(n);
                rcvBuf.limit(n).position(0);
                rcvBuf.get(b);
                ByteArray arr = ByteArray.from(b);
                synchronized (q) {
                    q.add(arr);
                }
            }
        }, "block-tap-reading").start();
        VProxyThread.create(() -> {
            while (true) {
                ByteArray arr = null;
                synchronized (q) {
                    if (!q.isEmpty()) {
                        arr = q.removeFirst();
                    }
                }
                if (arr == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {
                    }
                    continue;
                }
                arr.set(0, (byte) 0xaa);
                arr.set(1, (byte) 0xbb);
                arr.set(2, (byte) 0xcc);
                sndBuf.limit(sndBuf.capacity()).position(0);
                sndBuf.put(arr.toJavaArray());
                sndBuf.flip();
                int n = -1;
                try {
                    n = fd.write(sndBuf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("wrote " + n + " bytes");
            }
        }, "block-tap-writing").start();
    }
}
