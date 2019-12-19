# on 1 core vm

## Conclusion

The `vproxy` has the same performance as `haproxy` in tcp mode.  
Though `vproxy` is written in pure java.

## Env

* 3 1-core virtual machines separated on different hosts (physical machines).
* System: `Debian GNU/Linux 7`, `3.2.73-amd64`
* The client runs wrk.
* The backend runs nginx.
* The proxy server starts a vproxy on 80 and a haproxy on 8080(http)/8081(tcp), both use the same backend.

## Configuration

### client

Configure `/etc/hosts` file to add domain alias for nginx ip and proxy ip.

```
10.0.0.165 nginx.com
10.0.0.166 proxy.com
```

### nginx

```
apt-get install nginx
```

Modify the keepalive config

```
	keepalive_timeout 102400000;
        keepalive_requests 102400000;
```

No other changes.

### haproxy (http)

```
apt-get install haproxy
```

Add backend

```
listen ngx
        bind *:8080
        mode http
        server server1 10.18.201.165:80

listen ngx2
        bind *:8081
        mode tcp
        server server1 10.18.201.165:80
```

Remove some http in default scope (e.g. the log settings)

No other changes.

Note that default buffer size of haproxy is 16384 bytes.

### vproxy

```
add event-loop-group elg0
add upstream ups0
add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 addr 0.0.0.0:80 upstream ups0 in-buffer-size 512 out-buffer-size 512
add event-loop el0 to event-loop-group elg0
add server-group sg0 timeout 1000 period 3000 up 1 down 5 method wrr event-loop-group elg0
add server-group sg0 to upstream ups0
add server s0 to server-group sg0 address 10.0.0.165:80 ip 0.0.0.0 weight 10
```

The buffer is set to 512 to make sure the buffer can hold nginx returned data. Setting to bigger value will have no difference (theoretically).

## Statistics

### directly request nginx

nginx cpu reaches 99% when `-c 6`

```
./wrk -c 6 -t 1 -d 60 --latency http://nginx.com
Running 1m test @ http://nginx.com
  1 threads and 6 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   180.55us  165.24us   4.34ms   98.44%
    Req/Sec    35.79k     1.94k   38.89k    83.33%
  Latency Distribution
     50%  160.00us
     75%  175.00us
     90%  198.00us
     99%    0.93ms
  2136895 requests in 1.00m, 745.87MB read
Requests/sec:  35613.08
Transfer/sec:     12.43MB
```

And will have very little rps improvement (and more latency) if keeping increasing connection number, e.g. setting `-c 10`

```
./wrk -c 10 -t 1 -d 60 --latency http://nginx.com
Running 1m test @ http://nginx.com
  1 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   280.78us  182.56us   6.26ms   95.81%
    Req/Sec    37.21k     3.41k   40.31k    93.67%
  Latency Distribution
     50%  265.00us
     75%  319.00us
     90%  365.00us
     99%    1.03ms
  2221426 requests in 1.00m, 775.38MB read
Requests/sec:  37020.84
Transfer/sec:     12.92MB
```

### request haproxy (http)

Haproxy cpu usage is always around 66% when setting `-c 6`

```
./wrk -c 6 -t 1 -d 60 --latency http://proxy.com:8080
Running 1m test @ http://proxy.com:8080
  1 threads and 6 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   334.59us  228.71us   4.90ms   97.50%
    Req/Sec    19.10k     1.25k   22.17k    65.67%
  Latency Distribution
     50%  300.00us
     75%  331.00us
     90%  366.00us
     99%    1.71ms
  1140138 requests in 1.00m, 371.86MB read
Requests/sec:  19001.66
Transfer/sec:      6.20MB
```

It will go up to around 80% (sometimes 90%) when setting `-c 10`

```
./wrk -c 10 -t 1 -d 60 --latency http://proxy.com:8080
Running 1m test @ http://proxy.com:8080
  1 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   367.08us  227.56us   4.84ms   97.56%
    Req/Sec    28.74k     2.06k   32.83k    69.00%
  Latency Distribution
     50%  332.00us
     75%  369.00us
     90%  413.00us
     99%    1.74ms
  1715216 requests in 1.00m, 559.43MB read
Requests/sec:  28585.38
Transfer/sec:      9.32MB
```

The best qps performance I got for haproxy was `35880.69` when `-c 16`. But the data was captured using `-d 10`(only run for 10 seconds), so just consider it as the limit of haproxy in http mode.

### request haproxy (tcp)

The cpu usage of haproxy is around 40%.

```
./wrk -c 6 -t 1 -d 60 --latency http://proxy.com:8081
Running 1m test @ http://proxy.com:8081
  1 threads and 6 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   285.64us  217.25us   4.79ms   97.63%
    Req/Sec    22.53k     1.90k   27.32k    63.33%
  Latency Distribution
     50%  250.00us
     75%  286.00us
     90%  325.00us
     99%    1.57ms
  1344893 requests in 1.00m, 469.43MB read
Requests/sec:  22413.39
Transfer/sec:      7.82MB
```

It's around 60% when setting `-c 10`

```
./wrk -c 10 -t 1 -d 60 --latency http://proxy.com:8081
Running 1m test @ http://proxy.com:8081
  1 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   320.43us  232.19us   5.32ms   97.61%
    Req/Sec    33.20k     2.95k   38.03k    68.83%
  Latency Distribution
     50%  281.00us
     75%  318.00us
     90%  365.00us
     99%    1.66ms
  1981878 requests in 1.00m, 691.76MB read
Requests/sec:  33029.51
Transfer/sec:     11.53MB
```

`-c 16`

Cpu around 70%

```
./wrk -c 16 -t 1 -d 60 --latency http://proxy.com:8081
Running 1m test @ http://proxy.com:8081
  1 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   457.41us  260.96us   6.45ms   96.10%
    Req/Sec    36.53k     3.13k   40.66k    77.17%
  Latency Distribution
     50%  418.00us
     75%  467.00us
     90%  541.00us
     99%    1.96ms
  2180890 requests in 1.00m, 761.23MB read
Requests/sec:  36346.27
Transfer/sec:     12.69MB
```

The best qps performance I got was `37657.53` when `-c 22`

### request vproxy

The vproxy cpu usage is around 50% when setting `-c 6`

```
./wrk -c 6 -t 1 -d 60 --latency http://proxy.com:80
Running 1m test @ http://proxy.com:80
  1 threads and 6 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   283.12us  237.99us   5.07ms   97.65%
    Req/Sec    22.94k     1.51k   26.29k    64.17%
  Latency Distribution
     50%  248.00us
     75%  273.00us
     90%  306.00us
     99%    1.63ms
  1369460 requests in 1.00m, 478.00MB read
Requests/sec:  22823.64
Transfer/sec:      7.97MB
```

It will be up to 75%, but most of the time it's 55%-65% when setting `-c 10`

```
./wrk -c 10 -t 1 -d 60 --latency http://proxy.com:80
Running 1m test @ http://proxy.com:80
  1 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   328.52us  230.01us   4.41ms   97.37%
    Req/Sec    32.37k     2.99k   37.30k    74.50%
  Latency Distribution
     50%  289.00us
     75%  325.00us
     90%  376.00us
     99%    1.72ms
  1932421 requests in 1.00m, 674.50MB read
Requests/sec:  32204.10
Transfer/sec:     11.24MB
```

Keep increasing the connection until the limit:

`-c 16`

Cpu around 80%-90%

```
./wrk -c 16 -t 1 -d 60 --latency http://proxy.com:80
Running 1m test @ http://proxy.com:80
  1 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   451.30us  235.68us   5.23ms   95.84%
    Req/Sec    36.81k     2.31k   40.98k    87.50%
  Latency Distribution
     50%  416.00us
     75%  459.00us
     90%  515.00us
     99%    1.82ms
  2197241 requests in 1.00m, 766.94MB read
Requests/sec:  36619.31
Transfer/sec:     12.78MB
```

`-c 18`

Cpu still around 80%-90%, and the rps reaches nginx limit: around 38k.

```
./wrk -c 18 -t 1 -d 60 --latency http://proxy.com:80
Running 1m test @ http://proxy.com:80
  1 threads and 18 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   488.70us  220.54us   5.59ms   91.57%
    Req/Sec    37.68k     0.91k   39.99k    71.17%
  Latency Distribution
     50%  475.00us
     75%  527.00us
     90%  584.00us
     99%    1.54ms
  2249050 requests in 1.00m, 785.02MB read
Requests/sec:  37482.68
Transfer/sec:     13.08MB
```
