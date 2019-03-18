# Extended app

VProxy supports not only traditional loadbalancer, socks5 server, service mesh and discovery, but also some domain specific applications.

## How to use

The extended apps are defined in package `net.cassite.vproxyx`, each app entrance is a `void main0(String[])` method.

Use `system property -D` to specify the app's class.

NOTE: It's system property specified with `-D`, not a program argument.

```shell
-D+A:AppClass=$simple_name_of_a_class
```

The full command could be:

```shell
java -D+A:AppClass=$simple_name_of_a_class $JVM_OPTS -jar $the_jar_of_vproxy $application_args
#
# or
#
java -D+A:AppClass=$simple_name_of_a_class $JVM_OPTS net.cassite.vproxy.app.Main $application_args
```

e.g.

```
java -D+A:AppClass=WebSocksProxyServer -jar vproxy.jar listen 18686 auth alice:pasSw0rD,bob:PaSsw0Rd
```

## Available apps

### AppClass=WebSocksProxyServer

A proxy server that can proxy raw tcp flow even when it's behind a websocket gateway.

See [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) for more info.

#### Start arguments

* `listen`: an integer indicating which port the server should listen.
* `auth`: a sequence of `user:password` pairs split by `,`.
* `ssl`: a flag, indicating using tls connections. When this option is set, `pkcs12` and `pkcs12pswd` should also be given.
* `pkcs12`: the path to pkcs12 file, which should contain certificates and a private key.
* `pkcs12pswd`: the password of the pkcs12 file.
* `domain`: the domain name of current host (optional).

e.g.

```
listen 80 auth alice:pasSw0rD,bob:PaSsw0Rd
listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl pkcs12 ~/mycertkey.p12 pkcs12pswd myPassWord domain example.com
```

### AppClass=WebSocksAgent

An agent server run locally, which wraps websocks into socks5, so that other applications can use.

See [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) for more info.

#### Start arguments

(optional) full path of the configuration file

If not specified, the app will use `~/vproxy-websocks-agent.conf` instead.

The config file structure can be found [here](https://github.com/wkgcass/vproxy/blob/master/src/test/resources/websocks-agent-example.conf).
