# Service mesh example

## Example Repo

[https://github.com/wkgcass/vproxy-service-mesh-example](https://github.com/wkgcass/vproxy-service-mesh-example)

## Detail

The example will create 5 containers, see `NAMES` column:

```
CONTAINER ID        IMAGE                         COMMAND                  CREATED             STATUS              PORTS                    NAMES
74b454845559        vproxy-service-mesh-example   "/bin/bash /service-…"   4 hours ago         Up 4 hours                                   example-service-a2
8fb9da85c979        vproxy-service-mesh-example   "/bin/bash /service-…"   4 hours ago         Up 4 hours                                   example-service-b
55d019262156        vproxy-service-mesh-example   "/bin/bash /service-…"   4 hours ago         Up 4 hours                                   example-service-a
2e5000eba219        vproxy-service-mesh-example   "/bin/bash /frontend…"   4 hours ago         Up 4 hours          0.0.0.0:8080->8080/tcp   example-frontend
4a583c0804df        vproxy-service-mesh-example   "/bin/bash /lb.sh"       4 hours ago         Up 4 hours                                   example-lb
```

These containers form a network like this:

```
                                                                          +-----------------+
                                                                          |                 |
                                   +-.---.---.---.---.---.---.---.---.--->|   Service A 1   |
                                   |                                      |                 |
                                   |                                      +-----------------+
                                   .                                      |     sidecar     |
           +--------------+        |                                      +-----------------+
           |              |        .                                              ^
client---->|   Frontend   |-.--.---+                                              |
           |              |        ,                                              |
           +--------------+        |       +---------------+                      |
           |    sidecar   |        |       |               |                      +------------------+
           +--------------+        +.--.-->|   Service B   |                                         |
                  |                .       |               |                                         |
                  |                |       +---------------+                                         |
                  |                |       |    sidecar    |              +-----------------+        |
                  |                |       +---------------+              |                 |        |
                  |                .              ^                       |   Service A 2   |        |
                  |                +--.---.---.---|---.---.---.---.---.-->|                 |        |
                  |                               |                       +-----------------+        |
                  |                               |                       |     sidecar     |        |
                  |                               |                       +-----------------+        |
                  |                               |                                ^                 |
                  |                               |                                |                 |
                  |                               |                                |                 |
                  |                               +--------------------------------+-----------------+
                  |                               |
                  |                               |
                  |                               |
                  |                        +--------------+
                  |                        |              |
                  +----------------------->| SmartLBGroup |
                                           |              |
                                           +--------------+

line --------> is real netflow
line --.--.--> is logic netflow
```

The client requests `Frontend`. The `Frontend` will fetch data from `Service (A 1/2)/(B)` and respond to the client.

```
curl localhost:8080/service-b
{"service":"b","resp":"8fb9da85c979"}

curl localhost:8080/service-a
{"service":"a","resp":"74b454845559"}

curl localhost:8080/service-a
{"service":"a","resp":"55d019262156"}
```

The `service` field is service name, and the `resp` field is the container id.

When the service A and B launches, each service registers itself on local sidecar, and the frontend uses socks5 (provided by sidecar) to proxy the netflow.  
Details can be found in example code.

## Another Usage

You can use vproxy exactly same as the example code (lb and socks5), but you can also only use the lb part and ignore the socks5 part.

The lb port is specified in config, so, you can fix the lb ip (or a virtual ip of the lb) and port into you app's config.  
When a service starts, it should register it self into the sidecar. Requests sent by the service can be directly sent to the lb address instead of being proxied by the sidecar.  
New nodes will be automatically learned by the lb just like the example.
