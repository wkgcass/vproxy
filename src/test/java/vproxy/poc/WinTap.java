package vproxy.poc;

import vfd.windows.GeneralWindows;
import vfd.windows.Windows;
import vproxy.util.ByteArray;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class WinTap {
    public static void main(String[] args) throws IOException {
        System.loadLibrary("vfdwindows");
        Windows win = new GeneralWindows();
        long handle = win.createTapFD("tap0");
        ByteBuffer rcvBuf = ByteBuffer.allocateDirect(2048);
        ByteBuffer sndBuf = ByteBuffer.allocateDirect(2048);
        LinkedList<ByteArray> q = new LinkedList<>();
        new Thread(() -> {
            while (true) {
                int n;
                try {
                    n = win.read(handle, rcvBuf, 0, rcvBuf.capacity());
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
                System.out.println("read " + n + " bytes");
                byte[] b = new byte[n];
                rcvBuf.limit(n).position(0);
                rcvBuf.get(b);
                ByteArray arr = ByteArray.from(b);
                synchronized (q) {
                    q.add(arr);
                }
            }
        }).start();
        new Thread(() -> {
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
                int n = -1;
                try {
                    n = win.write(handle, sndBuf, 0, arr.length());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("wrote " + n + " bytes");
            }
        }).start();
    }
}
