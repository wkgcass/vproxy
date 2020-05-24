package vfd;

public class TapInfo {
    public final String dev;
    public final int fd;

    public TapInfo(String dev, int fd) {
        this.dev = dev;
        this.fd = fd;
    }

    @Override
    public String toString() {
        return "TapInfo{" +
            "dev='" + dev + '\'' +
            ", fd=" + fd +
            '}';
    }
}
