# on one core vm

## Pre

There are many features added (more than 150 commits) after the last benchmark.
Currently vproxy provides so many features which are almost the same as features that haproxy tcp loadbalancing provides.

In the last benchmark, vproxy is a little better because of the lack of features, so we should do another benchmark now, to see what should be improved in the later versions.

## Conclusion

For long connections: when vproxy and haproxy both not consuming the full cpu, vproxy is about 95% fast as haproxy, and about 90% fast as haproxy when cpu is 99%.  
For short connections: there's definitely something goes wrong in vproxy, which only is 70% fast as haproxy.

The performance is pretty good for long connections since it's java versus c.  
However it's too bad for short connections, and I will investigate into this before the next ALPHA release.

## Env

Three vm on three physical hosts.

* Client: 2 cores, wrk
* Proxy: 1 core, vproxy and haproxy
* Server: 4 cores, nginx (3 workers)

## Cases

### long connection cases

I start 3 wrk processes with 3 seconds interval to let connections separate on different nginx workers.

Each wrk process runs for 10 minutes.

The "connection (-c)" is set to `24/16/8`, which stand for "fully loaded", "busy" and "normal".

Note that the actual connection size is `24 * 3 / 16 * 3 / 8 * 3` because I started 3 wrk processes.

### short connection cases

The wrk process runs for 10 minutes.

The "connection (-c)" is set to `16/8/4`, which stand for "fully loaded", "busy" and "normal".  
The "thread (t)" is set to `2` to let wrk use both cores.

## Benchmark Summary

```
small packet (150B body) + long connection

haproxy 24 * 3
cpu: 80-96
rps: 81304.22

vproxy 24 * 3
cpu: 99
rps: 74018.12

haproxy 16 * 3
cpu: 76
rps: 72149.42

vproxy 16 * 3
cpu: 96
rps: 68873.87

haproxy 8 * 3
cpu: 70-76
rps: 55120.41

vproxy 8 * 3
cpu: 85-90
rps: 54164.53

small packet (150B body) + short connection

haproxy 16
cpu: 95-99
rps: 13344.09

vproxy 16
cpu: 95-99
rps: 8195.54

haproxy 8
cpu: 89-95
rps: 10389.50

vproxy 8
cpu: 95-99
rps: 7408.28

haproxy 4
cpu: 66-75
rps: 6982.97

vproxy 4
cpu: 75-85
rps: 6005.83
```

## benchmark detail

I'm not going to explain these commands and outputs in detail, since they are quite easy to understand.

```
sysctl -w net.ipv4.tcp_tw_recycle=1
sysctl -w net.ipv4.tcp_tw_reuse=1


cat /etc/nginx/nginx.conf
user www-data;
worker_processes 3;
pid /var/run/nginx.pid;

events {
	worker_connections 768;
}

http {
	sendfile on;
	tcp_nopush on;
	tcp_nodelay on;
	keepalive_timeout 102400000;
	keepalive_requests 102400000;
	types_hash_max_size 2048;

	access_log off;
	error_log off;

	gzip off;

	server {
		root /usr/share/nginx/www;
		index index.html index.htm;

		location / {
			try_files $uri $uri/ /index.html;
		}
	}
}

cat /usr/share/nginx/www/index.html
<html>
<head>
<title>Welcome to nginx!</title>
</head>
<body bgcolor="white" text="black">
<center><h1>Welcome to nginx!</h1></center>
</body>
</html>


nginx -V
nginx version: nginx/1.2.1
TLS SNI support enabled
configure arguments: --prefix=/etc/nginx --conf-path=/etc/nginx/nginx.conf --error-log-path=/var/log/nginx/error.log --http-client-body-temp-path=/var/lib/nginx/body --http-fastcgi-temp-path=/var/lib/nginx/fastcgi --http-log-path=/var/log/nginx/access.log --http-proxy-temp-path=/var/lib/nginx/proxy --http-scgi-temp-path=/var/lib/nginx/scgi --http-uwsgi-temp-path=/var/lib/nginx/uwsgi --lock-path=/var/lock/nginx.lock --pid-path=/var/run/nginx.pid --with-pcre-jit --with-debug --with-http_addition_module --with-http_dav_module --with-http_geoip_module --with-http_gzip_static_module --with-http_image_filter_module --with-http_realip_module --with-http_stub_status_module --with-http_ssl_module --with-http_sub_module --with-http_xslt_module --with-ipv6 --with-sha1=/usr/include/openssl --with-md5=/usr/include/openssl --with-mail --with-mail_ssl_module --add-module=/home/lamby/temp/cdt.20170713090605.MNoIIuBKCe.ags.nginx/nginx-1.2.1/debian/modules/nginx-auth-pam --add-module=/home/lamby/temp/cdt.20170713090605.MNoIIuBKCe.ags.nginx/nginx-1.2.1/debian/modules/nginx-echo --add-module=/home/lamby/temp/cdt.20170713090605.MNoIIuBKCe.ags.nginx/nginx-1.2.1/debian/modules/nginx-upstream-fair --add-module=/home/lamby/temp/cdt.20170713090605.MNoIIuBKCe.ags.nginx/nginx-1.2.1/debian/modules/nginx-dav-ext-module



cat /etc/haproxy/haproxy.cfg
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
	timeout client 90s
	timeout server 90s
	timeout connect 4s
	timeout http-keep-alive 102400s

frontend proxy-tcp
	bind :8081
	mode tcp
	default_backend ngx-tcp

backend ngx-tcp
	mode tcp
	server s1 10.18.202.114:80




cat vproxy.conf
add event-loop-group elg0
add upstream ups0
add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 addr 0.0.0.0:80 upstream ups0 in-buffer-size 512 out-buffer-size 512
add event-loop el0 to event-loop-group elg0
add server-group sg0 timeout 1000 period 3000 up 1 down 5 method wrr event-loop-group elg0
add server-group sg0 to upstream ups0 weight 10
add server s0 to server-group sg0 address 10.18.202.114:80 ip 0.0.0.0 weight 10



haproxy 24 * 3
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.05ms    1.14ms 204.93ms   91.00%
    Req/Sec    27.26k     4.38k   51.81k    69.76%
  Latency Distribution
     50%  705.00us
     75%    1.06ms
     90%    2.04ms
     99%    5.16ms
  16273357 requests in 10.00m, 5.55GB read
Requests/sec:  27121.29
Transfer/sec:      9.47MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.05ms    1.14ms 205.57ms   91.03%
    Req/Sec    27.21k     4.42k   52.06k    69.71%
  Latency Distribution
     50%  705.00us
     75%    1.06ms
     90%    2.04ms
     99%    5.15ms
  16243491 requests in 10.00m, 5.54GB read
Requests/sec:  27071.09
Transfer/sec:      9.45MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.04ms    1.04ms  25.17ms   90.42%
    Req/Sec    27.25k     4.39k   49.97k    69.83%
  Latency Distribution
     50%  706.00us
     75%    1.06ms
     90%    2.03ms
     99%    5.09ms
  16267819 requests in 10.00m, 5.55GB read
Requests/sec:  27111.84
Transfer/sec:      9.46MB


vproxy 24 * 3
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.15ms    1.14ms  21.97ms   90.13%
    Req/Sec    24.81k     4.28k   49.58k    72.18%
  Latency Distribution
     50%  770.00us
     75%    1.20ms
     90%    2.26ms
     99%    5.65ms
  14809970 requests in 10.00m, 5.05GB read
Requests/sec:  24680.32
Transfer/sec:      8.61MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.15ms    1.11ms  25.36ms   89.90%
    Req/Sec    24.55k     4.20k   49.53k    73.67%
  Latency Distribution
     50%  775.00us
     75%    1.22ms
     90%    2.29ms
     99%    5.49ms
  14660230 requests in 10.00m, 5.00GB read
Requests/sec:  24430.91
Transfer/sec:      8.53MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.13ms    1.12ms  26.72ms   90.23%
    Req/Sec    25.03k     4.36k   48.66k    73.38%
  Latency Distribution
     50%  762.00us
     75%    1.18ms
     90%    2.21ms
     99%    5.47ms
  14945549 requests in 10.00m, 5.09GB read
Requests/sec:  24906.89
Transfer/sec:      8.69MB


haproxy 16 * 3
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   848.43us    3.24ms 206.39ms   99.13%
    Req/Sec    24.19k     3.66k   41.07k    71.53%
  Latency Distribution
     50%  508.00us
     75%  836.00us
     90%    1.49ms
     99%    3.93ms
  14441105 requests in 10.00m, 4.92GB read
Requests/sec:  24067.05
Transfer/sec:      8.40MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.85ms    3.27ms 206.62ms   99.14%
    Req/Sec    24.18k     3.59k   39.46k    70.55%
  Latency Distribution
     50%  508.00us
     75%  839.00us
     90%    1.49ms
     99%    3.96ms
  14437644 requests in 10.00m, 4.92GB read
Requests/sec:  24061.08
Transfer/sec:      8.40MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.86ms    3.46ms 206.90ms   99.27%
    Req/Sec    24.14k     3.70k   41.22k    72.13%
  Latency Distribution
     50%  509.00us
     75%  838.00us
     90%    1.49ms
     99%    3.93ms
  14414052 requests in 10.00m, 4.91GB read
Requests/sec:  24021.29
Transfer/sec:      8.38MB



vproxy 16 * 3
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   848.36us    0.88ms  24.98ms   90.17%
    Req/Sec    22.80k     3.68k   43.40k    70.35%
  Latency Distribution
     50%  537.00us
     75%    0.91ms
     90%    1.71ms
     99%    4.22ms
  13613410 requests in 10.00m, 4.64GB read
Requests/sec:  22685.68
Transfer/sec:      7.92MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   837.05us  849.59us  24.75ms   90.43%
    Req/Sec    22.77k     3.63k   41.81k    68.93%
  Latency Distribution
     50%  539.00us
     75%    0.91ms
     90%    1.64ms
     99%    4.08ms
  13591722 requests in 10.00m, 4.63GB read
Requests/sec:  22650.38
Transfer/sec:      7.91MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   813.18us  849.66us  24.65ms   90.61%
    Req/Sec    23.66k     3.82k   40.17k    68.37%
  Latency Distribution
     50%  524.00us
     75%  849.00us
     90%    1.59ms
     99%    4.14ms
  14124114 requests in 10.00m, 4.81GB read
Requests/sec:  23537.81
Transfer/sec:      8.22MB



haproxy 8 * 3
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   440.32us  235.19us  10.15ms   92.67%
    Req/Sec    18.51k     2.47k   28.62k    70.57%
  Latency Distribution
     50%  407.00us
     75%  502.00us
     90%  625.00us
     99%    1.24ms
  11049562 requests in 10.00m, 3.77GB read
Requests/sec:  18415.82
Transfer/sec:      6.43MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   440.69us  233.14us  10.82ms   92.37%
    Req/Sec    18.50k     2.40k   27.27k    69.53%
  Latency Distribution
     50%  406.00us
     75%  502.00us
     90%  626.00us
     99%    1.26ms
  11044626 requests in 10.00m, 3.76GB read
Requests/sec:  18407.50
Transfer/sec:      6.43MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   444.35us  240.56us  13.36ms   92.90%
    Req/Sec    18.39k     2.48k   32.41k    71.02%
  Latency Distribution
     50%  409.00us
     75%  505.00us
     90%  629.00us
     99%    1.31ms
  10978285 requests in 10.00m, 3.74GB read
Requests/sec:  18297.09
Transfer/sec:      6.39MB



vproxy 8 * 3
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   457.97us  301.94us  16.02ms   94.31%
    Req/Sec    18.13k     2.77k   31.92k    69.12%
  Latency Distribution
     50%  412.00us
     75%  522.00us
     90%  660.00us
     99%    1.61ms
  10826704 requests in 10.00m, 3.69GB read
Requests/sec:  18044.36
Transfer/sec:      6.30MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   455.43us  297.94us  16.48ms   94.06%
    Req/Sec    18.22k     2.73k   26.92k    67.92%
  Latency Distribution
     50%  406.00us
     75%  520.00us
     90%  660.00us
     99%    1.59ms
  10875936 requests in 10.00m, 3.71GB read
Requests/sec:  18126.33
Transfer/sec:      6.33MB
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   458.68us  290.51us  12.58ms   93.66%
    Req/Sec    18.08k     2.82k   31.82k    69.10%
  Latency Distribution
     50%  408.00us
     75%  523.00us
     90%  665.00us
     99%    1.60ms
  10796391 requests in 10.00m, 3.68GB read
Requests/sec:  17993.84
Transfer/sec:      6.28MB




cat /etc/nginx/nginx.conf
user www-data;
worker_processes 3;
pid /var/run/nginx.pid;

events {
	worker_connections 768;
}

http {
	sendfile on;
	tcp_nopush on;
	tcp_nodelay on;
        keepalive_timeout 0;
	# keepalive_timeout 102400000;
	# keepalive_requests 102400000;
	types_hash_max_size 2048;

	access_log off;
	error_log off;

	gzip off;

	server {
		root /usr/share/nginx/www;
		index index.html index.htm;

		location / {
			try_files $uri $uri/ /index.html;
		}
	}
}







haproxy 16
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.07ms  384.07us  26.22ms   90.17%
    Req/Sec     6.71k   777.88     8.04k    64.02%
  Latency Distribution
     50%    1.02ms
     75%    1.20ms
     90%    1.37ms
     99%    2.01ms
  8006539 requests in 10.00m, 2.69GB read
Requests/sec:  13344.09
Transfer/sec:      4.59MB



vproxy 16
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.81ms  455.19us  30.21ms   82.82%
    Req/Sec     4.12k   297.47     4.84k    81.66%
  Latency Distribution
     50%    1.77ms
     75%    2.00ms
     90%    2.25ms
     99%    3.10ms
  4917586 requests in 10.00m, 1.65GB read
Requests/sec:   8195.54
Transfer/sec:      2.82MB


haproxy 8
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   646.52us  240.63us  20.82ms   93.33%
    Req/Sec     5.22k   452.06     6.01k    76.17%
  Latency Distribution
     50%  616.00us
     75%  700.00us
     90%  799.00us
     99%    1.29ms
  6233771 requests in 10.00m, 2.10GB read
Requests/sec:  10389.50
Transfer/sec:      3.58MB



vproxy 8
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.96ms  364.33us  25.32ms   91.00%
    Req/Sec     3.72k   392.76     4.34k    81.30%
  Latency Distribution
     50%    0.89ms
     75%    1.03ms
     90%    1.23ms
     99%    2.19ms
  4445110 requests in 10.00m, 1.49GB read
Requests/sec:   7408.28
Transfer/sec:      2.55MB



haproxy 4
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   461.76us  198.81us  11.02ms   97.87%
    Req/Sec     3.51k   237.78     3.96k    69.92%
  Latency Distribution
     50%  436.00us
     75%  487.00us
     90%  545.00us
     99%    0.91ms
  4189814 requests in 10.00m, 1.41GB read
Requests/sec:   6982.97
Transfer/sec:      2.40MB


vproxy 4
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   548.94us  230.78us  13.91ms   97.11%
    Req/Sec     3.02k   184.09     3.37k    75.66%
  Latency Distribution
     50%  516.00us
     75%  572.00us
     90%  641.00us
     99%    1.55ms
  3603534 requests in 10.00m, 1.21GB read
Requests/sec:   6005.83
Transfer/sec:      2.07MB
```
