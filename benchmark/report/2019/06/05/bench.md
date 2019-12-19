# Benchmark

## env

The proxy runs on a one-core vm, runs vproxy and haproxy;  
The client is on a two-core vm, runs wrk and h2load;  
The server is on a four-core vm, runs nginx;

## tests

1. http/2 tests. the proxy should add `X-Forwareded-For` header.
2. tcp tests. the proxy only forwards data between nodes.
3. tcp short connection tests. the proxy only forwards data between nodes, and the connection closes as soon as all data transfered.

## conclusion

http/2: vproxy is much better than haproxy: vproxy(112650.86 req/s) and haproxy(42658.64 req/s).  
tcp long connection: vproxy is almost the same as haproxy: vproxy(173081.94 req/s) and haproxy(175571.55 req/s).  
tcp short connection: vproxy is not doing well when running short connections: vproxy(6511.30 req/s) and haproxy(10052.61 req/s).

## appendix

### vproxy config

vproxy listens on 8800 for h2, 9900 for tcp, 10800 for tcp (short connection);

```
add server-group sg-tcp timeout 1000 period 5000 up 1 down 2 method wrr event-loop-group (control-elg)
add server-group sg-short timeout 1000 period 5000 up 1 down 2 method wrr event-loop-group (control-elg)
add server-group sg0 timeout 1000 period 5000 up 1 down 2 method wrr event-loop-group (control-elg)
add upstream ups0
add upstream ups-tcp
add upstream ups-short
add server-group sg0 to upstream ups0 weight 10
add server-group sg-tcp to upstream ups-tcp weight 10
add server-group sg-short to upstream ups-short weight 10
add tcp-lb lb-short acceptor-elg (acceptor-elg) event-loop-group (worker-elg) address 0.0.0.0:10800 upstream ups-short timeout 900000 in-buffer-size 16384 out-buffer-size 16384 protocol tcp
add tcp-lb tl-tcp acceptor-elg (acceptor-elg) event-loop-group (worker-elg) address 0.0.0.0:9900 upstream ups-tcp timeout 900000 in-buffer-size 16384 out-buffer-size 16384 protocol tcp
add tcp-lb tl0 acceptor-elg (acceptor-elg) event-loop-group (worker-elg) address 0.0.0.0:8800 upstream ups timeout 900000 in-buffer-size 16384 out-buffer-size 16384 protocol h2
add server svr-tcp-0 to server-group sg-tcp address 10.18.198.191:8080 weight 10
add server svr-tcp-1 to server-group sg-tcp address 10.18.198.191:8081 weight 10
add server svr-short-1 to server-group sg-short address 10.18.198.191:10080 weight 10
add server svr-short-2 to server-group sg-short address 10.18.198.191:10081 weight 10
add server svr0 to server-group sg0 address 10.18.198.191:8080 weight 10
add server svr1 to server-group sg0 address 10.18.198.191:8081 weight 10
```

### haproxy config

haproxy listens on 8080 for h2, 9090 for tcp, 10080 for tcp (short connection);

```
global
	log /dev/null	local0
	log /dev/null	local1 notice
	chroot /var/lib/haproxy
	stats socket /run/haproxy/admin.sock mode 660 level admin
	stats timeout 30s
	user haproxy
	group haproxy
	daemon

defaults
        timeout connect 5000
        timeout client  50000
        timeout server  50000
	errorfile 400 /etc/haproxy/errors/400.http
	errorfile 403 /etc/haproxy/errors/403.http
	errorfile 408 /etc/haproxy/errors/408.http
	errorfile 500 /etc/haproxy/errors/500.http
	errorfile 502 /etc/haproxy/errors/502.http
	errorfile 503 /etc/haproxy/errors/503.http
	errorfile 504 /etc/haproxy/errors/504.http

frontend f
	bind :8080 proto h2
	mode http
	option http-use-htx
	option forwardfor
	default_backend b

backend b
	mode http
	option http-use-htx
	server s1 10.18.198.191:8080 proto h2
	server s2 10.18.198.191:8081 proto h2

frontend tcp-f
	bind :9090
	mode tcp
	default_backend tcp-b

backend tcp-b
	mode tcp
	server s1 10.18.198.191:8080
	server s2 10.18.198.191:8081

frontend short-b
	bind :10080
	mode tcp
	default_backend short-b

backend short-b
	mode tcp
	server s3 10.18.198.191:10080
	server s4 10.18.198.191:10081
```

### nginx config

nginx listens on 8080,8081 for h2, 10080,10081 for http;

```
user www-data;
worker_processes 4;
pid /run/nginx.pid;
include /etc/nginx/modules-enabled/*.conf;

events {
	worker_connections 768;
}

http {

	sendfile on;
	tcp_nopush on;
	tcp_nodelay on;

	access_log /dev/null;
	error_log /dev/null;

	http2_max_requests 2147483647;
	keepalive_requests 0;

	server {
		listen 8080 http2 reuseport;
		location / {
			root html;
			index index.html index.htm;
		}
	}

	server {
		listen 8081 http2 reuseport;
		location / {
			root html;
			index index.html index.htm;
		}
	}

	server {
		listen 10080 reuseport;
		location / {
			root html;
			index index.html index.htm;
		}
	}

	server {
		listen 10081 reuseport;
		location / {
			root html;
			index index.html index.htm;
		}
	}
}
```

### results

```
h2load --threads=2 --clients=16 --max-concurrent-streams=48 --duration=300 --warm-up-time=10 "http://proxy.com:$V"
starting benchmark...
spawning thread #0: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
spawning thread #1: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
Warm-up started for thread #0.
progress: 12% of clients started
progress: 25% of clients started
progress: 37% of clients started
progress: 50% of clients started
progress: 62% of clients started
progress: 75% of clients started
progress: 87% of clients started
progress: 100% of clients started
Application protocol: h2c
Warm-up started for thread #1.
Warm-up phase is over for thread #0.
Main benchmark duration is started for thread #0.
Warm-up phase is over for thread #1.
Main benchmark duration is started for thread #1.
Main benchmark duration is over for thread #1. Stopping all clients.
Stopped all clients for thread #1
Main benchmark duration is over for thread #0. Stopping all clients.
Stopped all clients for thread #0

finished in 310.00s, 112650.86 req/s, 79.28MB/s
requests: 33795257 total, 33795257 started, 33795257 done, 33795257 succeeded, 0 failed, 0 errored, 0 timeout
status codes: 33795260 2xx, 0 3xx, 0 4xx, 0 5xx
traffic: 23.23GB (24940900554) total, 3.51GB (3771490712) headers (space savings 38.98%), 19.90GB (21371778604) data
                     min         max         mean         sd        +/- sd
time for request:     1.17ms     30.61ms      6.75ms      1.79ms    76.00%
time for connect:        0us         0us         0us         0us     0.00%
time to 1st byte:        0us         0us         0us         0us     0.00%
req/s           :    5961.18     7633.21     7040.67      561.63    75.00%
```

```
h2load --threads=2 --clients=16 --max-concurrent-streams=48 --duration=300 --warm-up-time=10 "http://proxy.com:$HA"
starting benchmark...
spawning thread #0: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
spawning thread #1: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
Warm-up started for thread #0.
progress: 12% of clients started
progress: 25% of clients started
progress: 37% of clients started
progress: 50% of clients started
progress: 62% of clients started
progress: 75% of clients started
progress: 87% of clients started
progress: 100% of clients started
Warm-up started for thread #1.
Application protocol: h2c
Warm-up phase is over for thread #0.
Main benchmark duration is started for thread #0.
Warm-up phase is over for thread #1.
Main benchmark duration is started for thread #1.
Main benchmark duration is over for thread #1. Stopping all clients.
Stopped all clients for thread #1
Main benchmark duration is over for thread #0. Stopping all clients.
Stopped all clients for thread #0

finished in 310.00s, 42658.64 req/s, 30.72MB/s
requests: 12797591 total, 12797591 started, 12797591 done, 12797591 succeeded, 0 failed, 0 errored, 0 timeout
status codes: 12797593 2xx, 0 3xx, 0 4xx, 0 5xx
traffic: 9.00GB (9662877677) total, 1.43GB (1534191524) headers (space savings 34.46%), 7.54GB (8094182868) data
                     min         max         mean         sd        +/- sd
time for request:     8.04ms     45.07ms     17.97ms      1.79ms    84.02%
time for connect:        0us         0us         0us         0us     0.00%
time to 1st byte:        0us         0us         0us         0us     0.00%
req/s           :    2655.65     2676.42     2666.16        6.98    62.50%
```

```
h2load --threads=2 --clients=16 --max-concurrent-streams=48 --duration=300 --warm-up-time=10 "http://proxy.com:$TCP_V"
starting benchmark...
spawning thread #0: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
spawning thread #1: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
Warm-up started for thread #0.
Warm-up started for thread #1.
progress: 12% of clients started
progress: 25% of clients started
progress: 37% of clients started
progress: 50% of clients started
progress: 62% of clients started
progress: 75% of clients started
progress: 87% of clients started
progress: 100% of clients started
Application protocol: h2c
Warm-up phase is over for thread #1.
Main benchmark duration is started for thread #1.
Warm-up phase is over for thread #0.
Main benchmark duration is started for thread #0.
Main benchmark duration is over for thread #1. Stopping all clients.
Stopped all clients for thread #1
Main benchmark duration is over for thread #0. Stopping all clients.
Stopped all clients for thread #0

finished in 310.00s, 173081.94 req/s, 121.82MB/s
requests: 51924581 total, 51924581 started, 51924581 done, 51924581 succeeded, 0 failed, 0 errored, 0 timeout
status codes: 51924585 2xx, 0 3xx, 0 4xx, 0 5xx
traffic: 35.69GB (38320342148) total, 5.38GB (5773194988) headers (space savings 38.98%), 30.47GB (32714766946) data
                     min         max         mean         sd        +/- sd
time for request:      529us     28.59ms      4.40ms      2.02ms    76.59%
time for connect:        0us         0us         0us         0us     0.00%
time to 1st byte:        0us         0us         0us         0us     0.00%
req/s           :    8198.06    14411.47    10817.60     2056.76    56.25%
```

(NOTE: haproxy cpu not 99%, only about 91-92, the nginx)
```
h2load --threads=2 --clients=16 --max-concurrent-streams=48 --duration=300 --warm-up-time=10 "http://proxy.com:$TCP_HA"
starting benchmark...
spawning thread #0: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
spawning thread #1: 8 total client(s). Timing-based test with 10s of warm-up time and 300s of main duration for measurements.
Warm-up started for thread #0.
progress: 12% of clients started
progress: 25% of clients started
progress: 37% of clients started
progress: 50% of clients started
progress: 62% of clients started
progress: 75% of clients started
progress: 87% of clients started
progress: 100% of clients started
Warm-up started for thread #1.
Application protocol: h2c
Warm-up phase is over for thread #0.
Main benchmark duration is started for thread #0.
Warm-up phase is over for thread #1.
Main benchmark duration is started for thread #1.
Main benchmark duration is over for thread #0. Stopping all clients.
Stopped all clients for thread #0
Main benchmark duration is over for thread #1. Stopping all clients.
Stopped all clients for thread #1

finished in 310.00s, 175571.55 req/s, 123.57MB/s
requests: 52671466 total, 52671466 started, 52671466 done, 52671466 succeeded, 0 failed, 0 errored, 0 timeout
status codes: 52671465 2xx, 0 3xx, 0 4xx, 0 5xx
traffic: 36.20GB (38871541791) total, 5.48GB (5885936188) headers (space savings 38.98%), 31.06GB (33353636472) data
                     min         max         mean         sd        +/- sd
time for request:      484us     24.33ms      4.37ms      2.04ms    74.93%
time for connect:        0us         0us         0us         0us     0.00%
time to 1st byte:        0us         0us         0us         0us     0.00%
req/s           :    6912.67    15069.30    10973.21     3571.71    25.00%
```

```
./wrk -c 16 -t 2 -d 300 --latency "http://proxy.com:$SHORT_V"
Running 5m test @ http://proxy.com:10800
  2 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.32ms  440.82us  12.02ms   79.60%
    Req/Sec     3.27k   143.02     3.67k    85.87%
  Latency Distribution
     50%    2.26ms
     75%    2.49ms
     90%    2.80ms
     99%    3.74ms
  1953601 requests in 5.00m, 1.54GB read
Requests/sec:   6511.30
Transfer/sec:      5.25MB
```

```
./wrk -c 16 -t 2 -d 300 --latency "http://proxy.com:$SHORT_HA"
Running 5m test @ http://proxy.com:10080
  2 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.45ms  321.42us  20.58ms   81.44%
    Req/Sec     5.05k   299.10     5.72k    80.02%
  Latency Distribution
     50%    1.41ms
     75%    1.60ms
     90%    1.76ms
     99%    2.25ms
  3015926 requests in 5.00m, 2.37GB read
Requests/sec:  10052.61
Transfer/sec:      8.10MB
```
