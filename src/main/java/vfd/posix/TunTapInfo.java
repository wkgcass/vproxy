package vfd.posix;

public class TunTapInfo {
    public final String dev;
    public final int fd;

    public TunTapInfo(String dev, int fd) {
        this.dev = dev;
        this.fd = fd;
    }

    @Override
    public String toString() {
        return "TunTapInfo{" +
            "dev='" + dev + '\'' +
            ", fd=" + fd +
            '}';
    }
}
