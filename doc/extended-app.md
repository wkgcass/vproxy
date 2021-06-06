# Extended app

VProxy supports not only traditional loadbalancer, socks5 server, service mesh and discovery, but also some domain specific applications.

## How to use

The extended apps are defined in package `vproxy.extended/vproxyx` and `vproxy.app/vproxy.app.vproxyx`, each app entrance is a `void main0(String[])` method.

Use `system property -D` to specify the app's class.

NOTE: It's system property specified with `-D`, not a program argument. And the system property is `eploy`, the initial `D` is the java command line option.

```shell
-Deploy=$simple_name_of_a_class
```

The full command could be:

```shell
java -Deploy=$simple_name_of_a_class $JVM_OPTS -jar $the_jar_of_vproxy $application_args
#
# or
#
java -Deploy=$simple_name_of_a_class $JVM_OPTS vproxy.app.app.Main $application_args
```

e.g.

```
java -Deploy=WebSocksProxyServer -jar vproxy.jar listen 18686 auth alice:pasSw0rD,bob:PaSsw0Rd
```

## Available apps

### Deploy=HelloWorld

Start an http server and an udp echo server. Also clients are started to request the servers, in order to see the environment is ok and basic functionalities are ok.

### Deploy=Simple

The vproxy simple loadbalancer. You can start a fully functional loadbalancer in one shell command.

See [how-to-use](https://github.com/wkgcass/vproxy/blob/master/doc/how-to-use.md) for more info.

### Deploy=Daemon

It's a daemon process specially designed for `Systemd`, used to launch, reload or auto-recover vproxy processes.

#### Start arguments

All accepted startup args are used to launch the vproxy subprocess.

### Deploy=WebSocksProxyServer

A proxy server that can proxy raw tcp flow even when it's behind a websocket gateway.

See [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) for more info.

#### Start arguments

* `listen`: an integer indicating which port the server should listen.
* `auth`: a sequence of `user:password` pairs split by `,`.
* `ssl`: a flag, indicating using tls connections. When this option is set, (`pkcs12` and `pkcs12pswd`) or (`certpem` and `keypem`) should also be given. Only one of pkcs and pem can be set.
* `pkcs12`: the path to pkcs12 file, which should contain certificates and a private key.
* `pkcs12pswd`: the password of the pkcs12 file.
* `certpem`: the certificate file path. Multiple certificates are separated with `,`.
* `keypem`: the private key file path.
* `domain`: `[optional]` the domain name of current host (optional).
* `redirectport`: `[optional]` bind a port and return http 3xx to redirect the client(browser) to the `listen` port.
* `kcp`: `[optional]` enable kcp transporting, the listening port will be the same as what `listen` specifies.
* `webroot`: `[optional]` the root directory of the website, you may use it to respond some static pages.

e.g.

```
listen 80 auth alice:pasSw0rD,bob:PaSsw0Rd
listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl pkcs12 ~/mycertkey.p12 pkcs12pswd myPassWord domain example.com

listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl \
        certpem /etc/letsencrypt/live/example.com/cert.pem,/etc/letsencrypt/live/example.com/chain.pem \
        keypem /etc/letsencrypt/live/example.com/privkey.pem \
        domain example.com \
        redirectport 80 \
        kcp \
        webroot /var/www/html
```

### Deploy=WebSocksProxyAgent

An agent server run locally, which wraps websocks into socks5, so that other applications can use.

See [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) for more info.

#### Start arguments

(optional) full path of the configuration file

If not specified, the app will use `~/.vproxy/vpws-agent.conf` instead.

The config file structure can be found [here](https://github.com/wkgcass/vproxy/blob/master/doc/websocks-agent-example.conf).

### Deploy=KcpTun

Network acceleration using KCP. You need to start a remote server which is fast to request your desired target, and a client run locally to create a tunnel between client and server.

See [vproxy kcp tunnel](https://github.com/wkgcass/vproxy/blob/master/doc/vproxy-kcp-tunnel.md) for more info.

#### Start arguments

* `mode`: running mode. enum: client or server
* `bind`: the listening port. for client, bind TCP on 127.0.0.1:$bind, for server, bind UDP on 0.0.0.0:$bind
* `target`: the connecting target. for client, set to the server ip:port, for server, set to the target ip:port
* `fast`: the retransmission configuration. enum: 1 or 2 or 3 or 4

e.g.

```
mode client bind 50010 target 100.1.2.3:20010 fast 3
mode server bind 20010 target google.com fast 3
```
