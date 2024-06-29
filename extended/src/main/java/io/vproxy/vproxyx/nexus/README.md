# ProxyNexus

`ProxyNexus` is a proxy mesh based on QUIC.

## Protocol

### Control Messages

The control messages are based on `HTTP/1.1`.

#### Connection Initialization

When a QUIC connection establishes, the client MUST start a QUIC stream with an HTTP request:

```
POST /ctrl/v1.0/establish
{ "node": "<client node name>" }
```

The client MUST NOT send any other messages until receiving the response from the server.

When receiving the `establish` request, the server SHOULD validate the request message.  
The server considers the connection from peer endpoint to be `initialized` when it receives a valid
`establish` request, and MUST respond with the following HTTP response:

```
200
{ "node": "<server node name>" }
```

or MAY response with the following HTTP response if the request message is considered invalid:

```
400
<reason>
```

After sending a `200` response, the server considers the current stream as `client control stream`,
all further control messages from client to server WOULD go through this stream.

The client considers the connection to peer endpoint to be `initialized` when it receives a `200`
response of the `establish` request.  
After this, the client considers the current stream as `client control stream`, all further control
messages from client to server MUST go through this stream.

#### Link Status Advertisement

When the connection is `initialized`, the client MUST send `link status advertisement` to the server
through `client control stream`:

```
PUT /ctrl/v1.0/link
{
  "node": "<node name>",
  "path": [ "<node name>" ],
  "peers": [
    {
      "node": "<node name>",
      "cost": <int64>
    }
  ]
}
```

The message MUST contain all directly linked peers' info, including the peer of this connection.

The server MUST respond `200` or `204` to the client if the received message is considered valid.

The server MUST NOT send any requests to the client before receiving the first `link status advertisement`.

When receiving the first `link status advertisement`, the server MUST start a new QUIC stream.  
The stream is considered as `server control stream`. The server then MUST send `link status advertisement`
to the client on the `server control stream`.

The client MUST respond `200` or `204` to the server if the received message is considered valid.

Any node MAY send `link status advertisement` at any time, and they SHOULD send the message
when peer status changes.

#### Link Status Forwarding

The client or server MUST forward the `link status advertisement` message to other linked nodes.  
Before forwarding the message, the node MUST add the name of itself into the `path` list field.  
If a linked node's name is recorded in the `path` list, the linked node SHOULD NOT be forwarded to.

The forwarded message is exactly the same as `link status advertisement`.

#### Link Status Updating Interval

All nodes MUST send the `link status advertisement` on all initialized connections at an interval
more than or equal to `2` seconds and less than `10` seconds.

#### Keepalive

All nodes MAY send `keepalive` requests on initialized connections at any time:

```
GET /ctrl/v1.0/keepalive
```

The receiving side MUST respond with `204` response without payload.

### Proxy Message

The proxy protocol is similar to the `HTTP Connect` protocol.

The node which wants to initiate a proxy stream MUST send the following request:

```
CONNECT <target-host>:<target-port> HTTP/1.1\r\n
X-Nexus-Node-Path: <node1>[,<node2>...]
```

`X-Nexus-Node-Path` MUST be calculated by the node which initialized the proxy.  
It is a list of node names separated by `,`, which forms a `proxy chain`.

The receiving side extracts the list from `X-Nexus-Node-Path` header.  
If the list is empty, then the receiving side is considered as the last node of the `proxy chain`,
it MUST respond with:

```
200
<self node name>
```

If the list is not empty, the receiving side SHOULD extract and remove the first node from the list,
then validate whether the node is directly linked to, then proxy the request to that node.

#### Headers

Available headers:

```
X-Nexus-Node-Path:        <the proxy chain node list>
X-Nexus-Source-Node:      <the source node name>
X-Nexus-Destination-Node: <the destination node name>
X-Nexus-Trace-Id:         <random trace id>
X-Forwarded-For:          <client ip>
X-Forwarded-Port:         <client port>
```

The `X-Nexus-Node-Path` header must be specified, and other headers are optional, which can be used for
tracing, validating or performing optimization.
