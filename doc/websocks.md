# The WebSocks Protocol

## Abstraction

The WebSocks protocol enables plain tcp data proxy through a WebSocket gateway
 which does not allow raw tcp data to pass.

It is a combination of the WebSocket protocol (RFC 6455) and SOCKS Protocol
 Version 5 (RFC 1928). It follows WebSocket and SOCKS5 rfc documentations, in
 other words, net flow of the protocol is considered as valid WebSocket net flow,
 and the content inside the WebSocket frame is considered as valid SOCKS5 net
 flow.

Documentation of the implementation can be found [here](https://github.com/wkgcass/vproxy/blob/master/doc/extended-app.md).

## Introduction

We usually choose socks5 to let net flow pass through a firewall. However some
 PaaS platforms do not provide tcp gateways, which means that we cannot run a
 socks5 server on these platforms because the gateway rejects unrecognized
 netflow. Usually these gateways only allows http frames to pass.

After RFC 6455 defines the WebSocket protocol, more and more web applications
 start to use WebSocket for data transfering, as a result, more and more PaaS
 platforms start to enable WebSocket support.

The WebSocket protocol is a protocol that allows two-way communication between
 a client and a server, so we can put socks5 handshaking and any tcp net flow
 above the WebSocket net flow, to make a proxy.

Then there comes the WebSocks protocol.

## Protocol

The protocol is divided into two parts: the handshake part and the netflow part.

### Handshake

#### Client Upgrade

Handshake is started by the client.

The client should send the following packet to make the connection upgrade to
 WebSocket connection:

```
GET / HTTP/1.1\r\n
Upgrade: websocket\r\n
Connection: Upgrade\r\n
Host: $host\r\n
Sec-WebSocket-Key: $client_key\r\n
Sec-WebSocket-Version: 13\r\n
Sec-WebSocket-Protocol: $proxy_protocol\r\n
Authorization: Basic $auth\r\n
\r\n
```

There are some placeholders in this packet:

* `$host` is the hostname of the remote HTTP server
* `$client_key` is a random bytes sequence serialized with base64 encoding
* `$auth` is a base64 string which contains username and password, which follows
     RFC 7617 The 'Basic' HTTP Authentication Scheme, but we should make a little
     modification, see chapter `Auth` in this article.
* `$proxy_protocol` is a string represents which proxy protocol the client would
     like to use. This header may appear multiple times to let the server choose
     one protocol out of them.

Example:

```
GET / HTTP/1.1\r\n
Upgrade: websocket\r\n
Connection: Upgrade\r\n
Host: myexample.com\r\n
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n
Sec-WebSocket-Version: 13\r\n
Sec-WebSocket-Protocol: socks5\r\n
Authorization: Basic YWxpY2U6RTRqNkg5QVN3S0dWZ2tpTmNSajQ3Q0dmUkRsdjF2WW9vRHhFc0ZQRW9EVT0=\r\n
\r\n
```

#### Server Upgrade

When the server receives this packet from client, it should validate the packet
 and check whether the password matches the user in Authorization header, which
 will be discussed in chapter `Auth` in detail. If everything goes ok, the server
 should respond with a packet:

```
HTTP/1.1 101 Switching Protocols\r\n
Upgrade: websocket\r\n
Connection: Upgrade\r\n
Sec-Websocket-Accept: $server_accept\r\n
Sec-WebSocket-Protocol: $proxy_protocol\r\n
\r\n
```

There are some placeholders in this packet:

* `$server_accept` should follow RFC 6455 Chapter 1.3
* `$proxy_protocol` is a string represents the protocol that the server chooses.
     This header should appear only once.

> NOTE: The server is allowed to do anything if the client packet is invalid,
> e.g. respond with http status 400 or 401, or close or reset the connection.

#### WebSocket Maximum Payload Length Frame

When the client receives http 101 for connection upgrade, it should send a byte
 sequence with exactly 10 bytes. The WebSocks protocol does not care what is
 contained in these 10 bytes, but, in order to go through a WebSocket gateway,
 these bytes should be the first 10 bytes of a Websocket frame, and:

1. FIN bit should be set
2. opcode should be set to `2` which denotes a binary frame
3. MASK bit should NOT be set
4. enable all extended payload length
5. fill the extended payload length bytes with at least `2^62`, for now, we use
     `2^63-1` (which is `not required`). The 62 bits after the first one are
     reserved.

The byte sequence in signed byte decimal format is:

```
{
  130, 127, 127, -1, -1, -1, -1, -1, -1, -1,
}
```

In this way, a valid implementation of WebSocket gateway should allow the following
 8 exa bytes of raw tcp net flow of this connection to pass, we can assume that
 this is absolutely enough for any communication between the client and the server.

> NOTE: most implementations of WebSocket gateway will downgrade a http session
> to a tcp session when http status 101 is returned by the server. If you are
> sure that the implementation does not care the frame, you can consider these
> 10 bytes as reserved and you can carry your own meta data here.

When server receives 10 bytes, it should send 10 bytes same as described above
 to the client.

#### Socks5 handshake

Then the client and server should follow socks5 handshake procedure described in
 RFC 1928 Chapter 3, with a few additional recommendations.

* It's recommended for servers to allow and choose `0x00 NO AUTHENTICATION REQUIRED`
     in socks5 step, the reason will be described in chapter `Auth` in this article.
* It's recommended for clients to only set `NO AUTHENTICATION REQUIRED` in the
     method list to minimize the handshake netflow.

#### Other proxy protocols

We may support more protocols, but currently we only support socks5.

### Plain Data

After the socks5 handshake is done, the WebSocks handshake is done as well. The
 server should proxy client data to remote endpoint based on the negotiation result
 according to RFC 1928.

### Addition

#### WebSocket PONG

As specified in RFC 6455 Chapter 5.5.3, any endpoint can send an unsolicited PONG
 message. We use this type of message to keep a connection alive if it in a connection
 pool, otherwise the connection may be closed by WebSocket gateway or other intermediate
 network nodes.

The client may send PONG messages when the `WebSocket Maximum Payload Length Frame` has
 yet not sent. The server only parses the message, and should NOT reply.

Also, we make a restriction about the flags, mask and payload_len:

* the `FIN` bit should be SET.
* three reserved bits should NOT be set.
* the `mask` should NOT be set.
* the `payload_len` should be `0`.

Then the packet would be fixed to two bytes: `10001010 00000000`. This makes parsing
 easier, after all, we only use PONG to keep the connection alive and not for sending
 messages.

#### Combine Packets

The bytes after an upgrade http handshake can be combined into one packet.
 The server should allow more data than required, which mainly happens in the
 socks step. The RFC 1928 asks servers and clients to follow the steps one
 after one, however it won't hurt if just sending all bytes in one packet.

## Auth

### Authorization header

We use RFC 7617 the Basic HTTP authentication scheme.

As specified in RFC, we need to make the header like the following:

```
Authorization: Basic $(base64($username:$password))
```

However the password goes on plain text and may be sniffered by others, then the
 person can use the password to access the proxy server.

So we make a hash with a salt related to current minute (utc+0, unix timestamp).

```
$password = base64(sha256(
                concat(
                  base64(sha256($real_password)),
                  str($minute)
                )
            ))
```

where on the client side, `$minute` is set to `$current_utc_minute_decimal_digit`.

To get the `$current_utc_minute_decimal_digit`, you may write this (java as an example):

```
(System.currentTimeMillis() / 60_000) * 60_000
```

The server may have a little time difference with the client, so on server side,
 we should check all the following hashes and consider the input is valid if any
 of them passes:

* `$minute = $current_utc_minute_decimal_digit - 60_000`
* `$minute = $current_utc_minute_decimal_digit`
* `$minute = $current_utc_minute_decimal_digit + 60_000`

If someone happen to get your net flow, the one will only be able to use your
 account to access the server for max 2 minutes.

> NOTE: This procedure is not aimed at protecting net flow from being seen by
> others, which should be protected by the TLS protocol.

### Use Authorization instead of socks5 auth

As you can see above, the authentication is done before upgrading.

We know that both http and socks5 provide authentication methods, the reason we
 choose to use http instead of socks5 is in two concern:

#### Protocol is more simple if using http to do authentication

Authentication in socks5 require at least two more sequential packets between
 the client and the server, while http require no more packets but just a header.

#### socks5 data can be directly proxied

The modern software world is used to using socks5 for proxying tcp flow, the best
 way of using this protocol is provide a socks5 agent.

If the socks5 part of this proxy allows `NO AUTHENTICATION REQUIRED`, then the
 agent (usually running locally on 127.0.0.1 or ::1) can omit the socks5 part
 and directly proxy client data to the server when the WebSocket part is finished.
