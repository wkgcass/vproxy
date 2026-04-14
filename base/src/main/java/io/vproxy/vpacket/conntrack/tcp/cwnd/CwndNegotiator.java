package io.vproxy.vpacket.conntrack.tcp.cwnd;

/**
 * Strategy interface for computing a negotiated minimum cwnd
 * based on the local and remote cwnd values exchanged via
 * a custom TCP option.
 */
public interface CwndNegotiator {
    int computeMinCwnd(int cwnd);

    static CwndNegotiator createDefault() {
        return new SimpleCwndNegotiator();
    }
}
