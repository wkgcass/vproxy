# Service mesh example

## Example Repo

[https://github.com/wkgcass/vproxy-service-mesh-example](https://github.com/wkgcass/vproxy-service-mesh-example)

## Detail

The example will create 5 containers, see `NAMES` column:

```
CONTAINER ID        IMAGE                         COMMAND                  CREATED             STATUS              PORTS                    NAMES
8679ffb59411        vproxy-service-mesh-example   "/bin/bash /service-…"   6 seconds ago       Up 6 seconds                                 example-service-a2
7c6138d1b123        vproxy-service-mesh-example   "/bin/bash /service-…"   7 seconds ago       Up 7 seconds                                 example-service-b
908131c78cba        vproxy-service-mesh-example   "/bin/bash /service-…"   8 seconds ago       Up 7 seconds                                 example-service-a
27b420698fe0        vproxy-service-mesh-example   "/bin/bash /frontend…"   9 seconds ago       Up 8 seconds        0.0.0.0:8080->8080/tcp   example-frontend
d7f70ba0fefb        vproxy-service-mesh-example   "/bin/bash /autolb.sh"   9 seconds ago       Up 9 seconds                                 example-auto-lb
```

These containers form a network like this:

```
                                                                          +-----------------+
                                                                          |                 |
                                                                          |   Service A 1   |
                                                                          |                 |
                                                                          +-----------------+
                                   +-.---.---.---.---.---.---.---.---.--->|     sidecar     |
           +--------------+        |                                      +-----------------+
           |              |        .                                              ^
client---->|   Frontend   |        |                                              |
           |              |        ,                                              |
           +--------------+        |       +---------------+                      |
           |    sidecar   |-.--.---+       |               |                      +------------------+
           +--------------+        |       |   Service B   |                                         |
                  |                .       |               |                                         |
                  |                |       +---------------+                                         |
                  |                +.--.-->|    sidecar    |              +-----------------+        |
                  |                |       +---------------+              |                 |        |
                  |                .              ^                       |   Service A 2   |        |
                  |                |              |                       |                 |        |
                  |                .              |                       +-----------------+        |
                  |                +--.---.---.-------.---.---.---.---.-->|     sidecar     |        |
                  |                               |                       +-----------------+        |
                  |                               |                                ^                 |
                  |                               |                                |                 |
                  |                               |                                |                 |
                  |                               +--------------------------------+-----------------+
                  |                               |
                  |                               |
                  |                               |
                  |                        +---------------+
                  |                        |               |
                  +----------------------->|    AutoLB     |
                                           |               |
                                           +---------------+

line --------> is real netflow
line --.--.--> is logic netflow
```

The client requests `Frontend`. The `Frontend` will fetch data from `Service (A 1/2)/(B)` and respond to the client.

```
curl localhost:8080/service-b
{"service":"b","resp":"7c6138d1b123"}

curl localhost:8080/service-a
{"service":"a","resp":"908131c78cba"}

curl localhost:8080/service-a
{"service":"a","resp":"8679ffb59411"}
```

The `service` field is service name, and the `resp` field is the container id.

When the service A and B launches, each service registers itself on local sidecar, and the frontend uses socks5 (provided by sidecar) to proxy the netflow.  
Details can be found in example code.

## Another Usage

You can use vproxy exactly same as the example code (lb and socks5), but you can also only use the lb part and ignore the socks5 part.

The auto-lb config require you to specify the listen port for each service, so, you can fix the lb ip (or a virtual ip of the lb) and port into you app's config.  
When a service starts, it should register it self into the sidecar. Requests sent by the service can be directly sent to the lb address instead of being proxied by the sidecar.  
New nodes will be automatically learned by the lb just like the example.
