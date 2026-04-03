package io.vproxy.base.selector.wrap.streamed;

import io.vproxy.base.selector.wrap.arqudp.ArqUDPSocketFD;

/**
 * Listener interface for connection lifecycle events.
 * Replaces the generic Consumer callbacks with typed methods.
 */
public interface StreamConnectionStateCallback {
    /**
     * Called when the handshake is complete and the connection is ready.
     *
     * @param fd the underlying ARQ UDP socket
     */
    void onReady(ArqUDPSocketFD fd);

    /**
     * Called when the connection becomes invalid (handshake failed, error, etc).
     *
     * @param fd the underlying ARQ UDP socket
     */
    void onInvalid(ArqUDPSocketFD fd);

    /**
     * Called when a new stream is being accepted (server-side only).
     *
     * @param fd the new stream FD
     * @return true if the stream is accepted, false to reject
     */
    default boolean onAccept(StreamedFD fd) {
        return false;
    }
}
