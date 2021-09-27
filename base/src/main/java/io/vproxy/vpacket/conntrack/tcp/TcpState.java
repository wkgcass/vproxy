package vproxy.vpacket.conntrack.tcp;

public enum TcpState {
    LISTEN(false, false), // represents waiting for a connection request
    SYN_SENT(false, false), // represents waiting for a matching connection request after having sent a connection request
    SYN_RECEIVED(false, false), // represents waiting for a confirming connection request acknowledgement after having both received and sent a connection request
    ESTABLISHED(false, false), // represents an open connection, data received can be delivered to the user. The normal state for the data transfer phase of the connection
    FIN_WAIT_1(false, true), // represents waiting for a connection termination request from the remote TCP, or an acknowledgement of the connection termination request previously sent
    FIN_WAIT_2(false, true), // represents waiting for a connection termination request from the remote TCP
    CLOSE_WAIT(true, false), // represents waiting for a connection termination request from the local user
    CLOSING(true, true), // represents waiting for a connection termination request acknowledgment from the remote TCP
    LAST_ACK(true, true), // represents waiting for an acknowledgment of the connection termination request previously sent to the remote TCP (which includes an acknowledgment of its connection termination request)
    TIME_WAIT(true, true), // represents waiting for enough time to pass to be sure the remote TCP receiveed the acknowledgment of its connection termination request
    CLOSED(true, true), // represents no connection stat at all
    ;
    public final boolean remoteClosed;
    public final boolean finSent;

    TcpState(boolean remoteClosed, boolean finSent) {
        this.remoteClosed = remoteClosed;
        this.finSent = finSent;
    }
}
