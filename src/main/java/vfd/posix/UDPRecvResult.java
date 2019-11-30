package vfd.posix;

public class UDPRecvResult {
    public final VSocketAddress address;
    public final int len;

    public UDPRecvResult(VSocketAddress address, int len) {
        this.address = address;
        this.len = len;
    }

    @Override
    public String toString() {
        return "(addr=" + address + ", len=" + len + ")";
    }
}
