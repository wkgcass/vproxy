# vproxy kcp tunnel

VProxy now natively supports kcp, and built a self-defined protocol for kcp tunnel. You may use it in WebSocksProxyAgent and WebSocksProxyServer.

## protocol

The protocol is abstracted. You may fill you own implementation as long as it supports all functionality of the abstracted protocol. The protocol is not integrated with kcp, you may use it on any arq protocols.

VProxy implements a tunnel protocol similar to http/2. And the protocol is not integrated with kcp, you may use it on any arq protocols.

## message types

1. handshake: used to initiate the tunnel
2. keepalive: used to check whether the tunnel is still valid
3. keepalive ack: used to respond a keepalive message to keep the tunnel active
4. SYN: used to start a connection inside the tunnel
5. SYN-ACK: used to let client know server received the `SYN` message
6. PSH: used to send data between client and server
7. FIN: used to tell another endpoint that the sender endpoint will not send any data in the connection
8. PSH-FIN: (optional) used to tell another endpoint that the sender endpoint sends the last piece in the connection
9. RST: used to close the connection discarding any data remaining
10. shutdown: used to close the tunnel and all connections inside the tunnel

## states

The states are divided into two parts.

First part is for the tunnel states:

1. init: the client or server just initiated
2. handshake\_sent: handshake message is sent from client to server
3. ready: handshake message is received by client from server, or server receives handshake message from client
4. both endpoints may send `shutdown` message to close the tunnel with a reason in the message.

Second part is for the connection states:

1. none: the connection is just created
2. syn\_sent: the client sends `SYN` to the server
3. established: the client receives `SYN_ACK` from server, or server receives `SYN` from client
4. fin\_sent: `FIN` message sent
5. fin\_recv: `FIN` message received
6. dead: the connection is dead but user code has not explicitly call `close(fd)` yet
7. real\_closed: the fd is really closed by the client

## flow

1. the client sends a HANDSHAKE frame to the server
2. the server sends a HANDSHAKE-ACK frame to the client (now the tunnel established)
3. the client sends a SYN frame to the server
4. the server sends a SYN-ACK frame to the client (now the connection established)
5. the client/server can send PSH frames to each other
6. the client/server sends a FIN or PSH-FIN frame (now the connection on sender side transform into SYN\_SENT, and transform into SYN\_RECV on the reciever side)
7. another endpoint sends FIN or PSH-FIN at some time
8. when connection is established, both endpoints may send a RST frame to close the connection immediately
9. when tunnel is established, both endpoints may send a shutdown frame to close the tunnel, as well as all connections inside the tunnel

## frames

VProxy implemented the protocol using a frame set similar to `HTTP/2`.

### handshake

An empty `SETTINGS` frame.

### kepalive

A `PING` frame with an integer.

### keepalive ack

A `PING` frame with ack flag set and an integer same as which contained in the `keepalive` message.

### SYN

An empty (not standard, the frame.length is set to 0) `Headers` frame.

### SYN-ACK

An empty (not standard, the frame.length is set to 0) `Headers` frame.

### PSH

A `Data` frame containing the pushed data in the payload.

### FIN

A `Data` frame without payload and with close\_stream flag set.

### PSH-FIN

A `Data` frame with payload and with close\_stream flag set.

### RST

An empty (not standard, the frame.length is set to 0) `Headers` frame with close\_stream flag set.

### shutdown

A `GOAWAY` frame, with a string reason as the payload. The `stream id` and `error code` are not used.
