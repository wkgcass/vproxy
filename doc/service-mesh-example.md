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
```

These containers form a network like this:

```
                                                                          +-----------------+
                                                                          |                 |
                                   +------------------------------------->|   Service A 1   |
                                   |                                      |                 |
           +--------------+        |                                      +-----------------+
           |              |        |                                      |     sidecar     |
client---->|   Frontend   |        |                                      +-----------------+
           |              |        |
           +--------------+        |
           |    sidecar   |------->+
           +--------------+        |       +---------------+
                                   |       |               |
                                   +------>|   Service B   |
                                   |       |               |
                                   |       +---------------+
                                   |       |    sidecar    |
                                   |       +---------------+              +-----------------+
                                   |                                      |                 |
                                   +------------------------------------->|   Service A 2   |
                                                                          |                 |
                                                                          +-----------------+
                                                                          |     sidecar     |
                                                                          +-----------------+
```

The client requests `Frontend`. The `Frontend` will fetch data from `Service (A 1/2)/(B)` and respond to the client.

```
curl localhost:8080/service-b
{"service":"b","host":"8fb9da85c979","port":17729}

curl localhost:8080/service-a
{"service":"a","host":"74b454845559","port":28168}

curl localhost:8080/service-a
{"service":"a","host":"55d019262156","port":29315}
```

The `service` field is service name, the `host` field is the container id, and the `port` field is the listening port allocated by the sidecar.

When the service A and B launches, each service registers itself on local sidecar, and the frontend uses socks5 (provided by sidecar) to proxy the netflow.  
Details can be found in example code.

## Another Usage

You may also use lb instead of socks5 on the sidecar. When using lb, you should directly request the lb port of sidecar exposed on 127.0.0.1.
