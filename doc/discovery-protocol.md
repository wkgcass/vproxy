# Discovery Protocol

The vproxy supports node discovery and auto configuring. This doc explains what the vproxy node discovery can do and how it works.

> NOTE: all data structure is transfered via JSON (wrapped into RESP or HTTP).

## Usage

The discovery protocol gives vproxy the ability to scale without any host-unique configuration.

This solution is suitable for an idc that:

* has hundreds of endpoints in a cluster.
* all endpoints are reachable to each other.

Fault tolerance:

* when a server is unreachable, it will be removed in about 15 seconds.
* when a new server appears, it will be able to work in about 10 seconds.
* when a cleavage occurs, the nodes can still work properly.

## Node discovery

VProxy does not require you to manually "join" a new node into a cluster. The discovery is fully automated.

Each vproxy instance (let's call it a `node`) listens on a udp port and a tcp port. The udp port is used to receive and reply discovery existence messages, and the tcp port is used to receive and reply discovery data messages.

Each node caches the nodes info it already knows. It should sort them and calculate the hash when node cache changes.

And each node should make health checks to all nodes it knows on the tcp port. When a new node is discovered, it's health status should be set to `DOWN`. When the node is `UP`, it should be added into cache. When it's down for a long time, it should be removed from cache.

### Discovery existence message

UDP packet.

Wrapped into RESP.

```
{
  version: an integer. the version of the protocol. currently 1.
  type: enum {search, inform, leave}. the type of this message.
  nodeName: a string. name of node who sent this message.
  udpPort: an integer. the udp port that the sender is listening.
  tcpPort: an integer. the tcp port that the sender is listening.
  hash: a string. the hash value of the nodes info cached by the sender, encoded into base64.
}
```

The udp packet carries the sender's ip address, so the receiver will know which address to write back to. Sender will get the sender's receiving port via the message, and will `NOT` use the udp packet src port because they might be different.

### Discovery data message

HTTP request.

```
{
  nodes: an array of nodes. [
    nodeName: a string. name of the node.
    address: a string. ip address of the node.
    udpPort: an integer. udp port of the node.
    tcpPort: an integer. tcp port of the node.
    healthy: a boolean value represented by an integer. 0 for unhealthy and 1 for healthy.
  ]
}
```

Request and response will be handled in the same connection. The connection will be closed after all the response bytes are transferred.

### Procedure

1. When a new node starts, it will send `discovery data message` (udp) with `type=search` to all endpoints in a network and port range configured in the configuration file, e.g. `network 10.0.0.0/24 port-range 12300,12400`.  
    The rate is limited to 250 pps and 10 seconds interval between two search rounds when no neighbour node is found, which is rather low for any machine, but efficient enough (/24 network in about 1 second).  
    The rate is limited to 50 pps and 60 seconds interval when there is at least one neighbour node.  
    We use udp in this step because it's fast and connection-free.  
    We do not use broadcast or multicast, because some cloud providers do not support them, and we cannot limit the packet rate in application level if using broadcast or multicast.
2. The node who received the `type=search` udp message, will respond with a `type=inform` message if the hash mismatches.  
    Hash not same means that there are some nodes missing or redundant in the cache, and need to be fixed.  
    If the hash from the message and the hash in local are the same, no response will be sent.
3. When receiving the `type=inform` message, the node will compare the hash and will make a tcp connection to the remote node to fetch node list if hash values do not match.
4. The tcp requests and responses are `discovery data message`s. The sender and receiver will record all missing nodes into. They will not care about the redundant ones. Those nodes will be removed after being down for `detach-timeout (5 minutes)`.
5. The hash value only considers `healthy` node records. Those unhealthy nodes will not be calculated.
6. Addition: When receiving a `type=inform` message and hash mismatches, the node `MAY` pause sending udp packets and go into the interval.  
    This is used to limit the rate when something changes in a stable network, no need to make multiple tcp connections just to retrieve the same node list.  
    The node will NOT pause if it's just started. Because when two nodes start at the same time, they might not be able to find each other in the first search round if without this rule.
7. When a node is going to leave, it will send a `type=leave` message (udp) to all nodes it knows.  
    The rate is limited to 250 pps.

### Example

Let's assume we have three nodes: a new vproxy node which is just launched and not joined into the cluster, let's call it `A`; and two nodes that is working properly, let's call them `B` and `C`.

```
   A       B
          /
         /
        /
       C
```

A will send a udp packet with `type=search` to B and C. Both B and C will respond with `type=inform` packet to A because the hash values mismatch.  

```
--[hash(A)]-->
<-[hash(B,C)]--
   A       B
          /
         /
        /
       C
```

On receiving the response, A will make a tcp connection to B, and a tcp connection to C. Both connections will send A's local cached node list, which is `[A]`, and will fetch the node lists from B and C, which are both `[B,C]`.

```
  ---[A]--->
  <--[B,C]--
   A       B
          /
         /
        /
       C
```

B and C will add A into their local cache, and start health check on A. A will and B, C to its local cache, and starts health check on B and C.

```
   A-.-.-.-B
    \     /
     .   /
      \ /
       C
```

The health check will soon turn to UP. And A,B,C now find each other.

```
   A-------B
    \     /
     \   /
      \ /
       C
```

### Interfaces

The discovery lib can be registered with a `nodeListener`, when a node is up,down,or left, the corresponding listener event would be fired, with the node as the argument.

## The Khala network

VProxy builds a higher level network above the discvoery network, which is named `Khala` (The name is from game Starcraft).

The khala network lib let user code focus on service handling. Any khala node changes in local will be synchronized to remote automatically, and any remote changes will be known by user code via callback methods.

One discovery node can carry multiple khala nodes.

### Khala message

Khala messages use the same http server of discovery, but uri not same.

* A `type=khala.hash` message should carry hash of all cached khala nodes.
* A `type=khala.sync` message should carry all cached khala nodes. If the message contains no node data, the message will not have any effect on the local cache. Othersie, the differed nodes (missing and redundant) will be extracted and requests of `type=khala.local` will be made to those discovery nodes and sync data.
* A `type=khala.add` message should carry only local discovery node and the added khala node.
* A `type=khala.remove` message should carry only local discovery node and the removed khala node.
* A `type=khala.local` message should carry local discovery node and all "local" khala nodes.

Message type:

```
khala.hash
{
  hash: base64 of hash
}

khala.sync
[
  {
    node: { nodeName, address, udpPort, tcpPort, }
    khalaNodes: [ { service, zone, address, port, } ]
  }
]

response of khala.sync
{
  diff: [ { node: nodeName, address, udpPort, tcpPort, } ]
}

khala.add and khala.remove
{
  node: { nodeName, address, udpPort, tcpPort, }
  khalaNode: { service, zone, address, port, }
}

khala.local
{
  node: { nodeName, address, udpPort, tcpPort, }
  khalaNodes: [ { service, zone, address, port, } ]
}
```

### Procedure

The khala is based on vproxy discovery lib. So it knows when a discovery node is UP or DOWN or left. As a result, it can synchronize data with the new node as soon as possible, or drop nodes of a dead node.

1. When a new node joins, a `type=khala.local` message will be sent to the new node, and the new node will respond with a `type=khala-local` message.
2. When receiving a `type=khala.local` message, the lib will cover corresponding discovery node data with the data in message.
3. When a node is down or left, the lib will remove all data related to the discovery node.
4. When a node is added locally, the lib will send a `type=khala.add` message to inform other nodes about the new node.
5. When a node is removed locally, the lib will send a `type=khala.remove` message to inform other nodes about the removed node. Sending rules are the same as `type=khala-add` message.
6. For every 2 minutes, the lib chooses a nexus node randomly, and send a `type=khala.hash` and `type=khala.sync` message to sync node data.
7. When receiving `type=khala.hash` message, the node will reply a `type=khala.hash` data.
8. When receiving `type=khala.sync` message, the node will reply a `type=khala.sync` data, and differ the message nodes and local cached nodes. When a mismatch found, the lib will request the mismatched node with `type=khala-local` message, to fetch the remote khala nodes.

### Interfaces

1. addLocal: add a khala node
2. removeLocal: remove a khala node
3. `KhalaNodeListener`: fires when a khala node is up or down, with discovery node and khala node as the arguments.
