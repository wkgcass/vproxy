# LB Example

## Network Topology

Assume we have the following network topology graph:

```
                                  +--------> BACKEND1 (:80)
                                  |          10.0.2.1
                     4c           |
 CLIENT ---------> VPROXY --------+--------> BACKEND2 (:80)
10.0.0.1   10.0.0.10 | 10.0.2.10  |          10.0.2.2
                     |            |
                 10.0.3.10        +--------> BACKEND3 (:80)
                     |                       10.0.2.3
                     |
                     *
                 10.0.3.1
                   ADMIN
```

The vproxy's host has three ip addresses, one in the CLIENT network `10.0.0.0/24`, one in the backends' network `10.0.2.0/24`, one in the admin network `10.0.3.0/24`.

And the vproxy's host has 4 cores.

## BACKEND

```
apt-get install nginx
service nginx start
```

then every server listens on `0.0.0.0:80`.

## VPROXY

#### 1. Start

Start vproxy instance, and create a `RESPController` for management:

```
tmux

## then start vproxy in tmux terminal

java net.cassite.vproxy.app.Main resp-controller 10.0.3.10:16379 m1PasSw0rd
```

Start the vproxy and start a resp-controller and bind `10.0.3.10:16379` for the `ADMIN` to access.

#### 2. Use redis-cli

Start a `redis-cli` on `ADMIN`:

```
redis-cli -h 10.0.3.10 -p 16379 -a m1PasSw0rd
```

The following commands can be executed from the `redis-cli` (telnet also works).

#### 3. Threads

Create two event loop groups: one for accepting connections, and one for handling net flow.

```
add event-loop-group acceptor
add event-loop-group worker
```

We only create one event loop in the `acceptor` event loop group, however, if you expect that there are a lot of new connections to be handled, you can add more event loops into the `acceptor` event loop group.

And because that the host has 4 cores, we create 3 threads for handling net flow.

```
add event-loop acceptor1 to event-loop-group acceptor

add event-loop worker1 to event-loop-group worker
add event-loop worker2 to event-loop-group worker
add event-loop worker3 to event-loop-group worker
```

#### 4. Backends

Create a server group named `ngx`.

```
add server-group ngx timeout 500 period 1000 up 2 down 3 method wrr event-loop-group worker
```

We use the worker event loop group to run health check.

Attach backends to the group:

```
add server backend1 to server-group ngx address 10.0.2.1:80 weight 10
add server backend2 to server-group ngx address 10.0.2.2:80 weight 10
add server backend3 to server-group ngx address 10.0.2.3:80 weight 10
```

You can use `list-detail` to check current health check status.

```
list-detail server in server-group ngx
```

Create a `server-groups` resource, and attach group `ngx` to the new resource.

```
add server-groups backend-groups
add server-group ngx to server-groups backend-groups
```

#### 5. TCP LB

Create a loadbalancer and bind `10.0.0.10:80`.

```
add tcp-lb lb0 acceptor-elg acceptor event-loop-group worker address 10.0.0.10:80 server-groups backend-groups in-buffer-size 16384 out-buffer-size 16384
```

Then the tcp loadbalancer starts.

#### 6. Check and save config

You can run some special commands in vproxy console, they cannot be executed from `redis-cli`. (Unless you specify `allowSystemCallInNonStdIOController` on start up, but methods related to local filesystem or process will not be allowed for safety concern).

Check config:

```
System call: list config
```

Save config:

```
System call: save ~/vproxy.conf
```

Except for saving by hand, the config can be automatically saved in `~/.vproxy.last` for every hour. And if it's terminated by `SIGINT` or `SIGHUP`, or manually shutdown, the config will also be saved.

And last saved config will be loaded if the start up arguments not containing any `load` command.
