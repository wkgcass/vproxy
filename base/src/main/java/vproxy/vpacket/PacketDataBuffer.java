package vproxy.vpacket;

import vproxy.base.util.ByteArray;

public class PacketDataBuffer {
    public ByteArray fullbuf;
    public int pktOff; // packet offset
    public int pad; // padding length after the end of the packet
    public ByteArray pktBuf; // sub buffer of buf

    public PacketDataBuffer(ByteArray fullbuf, int pktOff, int pad) {
        this.fullbuf = fullbuf;
        this.pktOff = pktOff;
        this.pad = pad;
        if (pktOff == 0 && pad == 0) {
            this.pktBuf = fullbuf;
        } else {
            this.pktBuf = fullbuf.sub(pktOff, fullbuf.length() - pktOff - pad);
        }
    }

    public PacketDataBuffer(ByteArray pktBuf) {
        if (pktBuf == null) {
            return;
        }
        this.fullbuf = pktBuf;
        this.pktOff = 0;
        this.pad = 0;
        this.pktBuf = pktBuf;
    }

    public void clearBuffers() {
        this.fullbuf = null;
        this.pktOff = 0;
        this.pad = 0;
        this.pktBuf = null;
    }

    public PacketDataBuffer sub(int off) {
        return new PacketDataBuffer(fullbuf, this.pktOff + off, pad);
    }

    public PacketDataBuffer sub(int off, int len) {
        return new PacketDataBuffer(fullbuf, this.pktOff + off, fullbuf.length() - (this.pktOff + off) - len);
    }
}
