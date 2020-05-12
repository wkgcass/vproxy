package vfd;

public interface TapDatagramFD extends AbstractDatagramFD<NoSockAddr> {
    TapInfo getTap();
}
