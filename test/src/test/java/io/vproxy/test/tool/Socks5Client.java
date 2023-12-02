package io.vproxy.test.tool;

import io.vproxy.base.socks.AddressType;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.IP;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Socks5Client {
    private Client client;

    public Socks5Client(int port) {
        client = new Client(port);
    }

    public void connect(AddressType addressType, String addr, int port) throws IOException {
        client.connect();
        OutputStream os = client.socket.getOutputStream();
        os.write(new byte[]{5, 1, 0});
        int replyLen = 0;
        InputStream is = client.socket.getInputStream();
        byte[] buf = Utils.allocateByteArrayInitZero(32);
        //noinspection StatementWithEmptyBody
        while ((replyLen += is.read(buf)) < 2) {
        }
        if (buf[1] != 0)
            throw new RuntimeException("buf[1] != 0");
        byte[] toWrite = Utils.allocateByteArrayInitZero(1 + 1 + 1 + 1
            + (addressType == AddressType.domain ? (1 + addr.length()) : (addressType == AddressType.ipv4 ? 4 : 16))
            + 2);
        toWrite[0] = 5;
        toWrite[1] = 1;
        toWrite[2] = 0;
        toWrite[3] = addressType.code;
        int nextIdx;
        if (addressType == AddressType.ipv4) {
            byte[] ipv4 = IP.blockResolve(addr).getAddress();
            toWrite[4] = ipv4[0];
            toWrite[5] = ipv4[1];
            toWrite[6] = ipv4[2];
            toWrite[7] = ipv4[3];
            nextIdx = 8;
        } else if (addressType == AddressType.domain) {
            byte[] addrBytes = addr.getBytes();
            toWrite[4] = (byte) addrBytes.length;
            System.arraycopy(addrBytes, 0, toWrite, 5, addrBytes.length);
            nextIdx = 5 + addrBytes.length;
        } else {
            assert addressType == AddressType.ipv6;
            byte[] ipv6 = IP.blockResolve(addr).getAddress();
            toWrite[4] = ipv6[0];
            toWrite[5] = ipv6[1];
            toWrite[6] = ipv6[2];
            toWrite[7] = ipv6[3];
            //---
            toWrite[8] = ipv6[4];
            toWrite[9] = ipv6[5];
            toWrite[10] = ipv6[6];
            toWrite[11] = ipv6[7];
            //--
            toWrite[12] = ipv6[8];
            toWrite[13] = ipv6[9];
            toWrite[14] = ipv6[10];
            toWrite[15] = ipv6[11];
            // --
            toWrite[16] = ipv6[12];
            toWrite[17] = ipv6[13];
            toWrite[18] = ipv6[14];
            toWrite[19] = ipv6[15];
            nextIdx = 20;
        }
        toWrite[nextIdx] = (byte) ((port >> 8) & 0xFF);
        toWrite[nextIdx + 1] = (byte) (port & 0xFF);
        os.write(toWrite);
        //noinspection StatementWithEmptyBody
        while ((replyLen += is.read(buf)) < 10) {
        }
        if (buf[1] != 0)
            throw new RuntimeException("buf[1] != 0");
    }

    public String sendAndRecv(String data, int recvLen) throws IOException {
        return client.sendAndRecv(data, recvLen);
    }

    public void close() {
    }
}
