# vproxy

[中文文档](https://github.com/wkgcass/vproxy/blob/master/README_ZH.md)

## Intro

VProxy is a zero-dependency TCP Loadbalancer based on Java NIO. The project only requires Java 11 to run.

Clone it, javac it, then everything is ready for running.

(Gradlew and Dockerfile is also provided)

## Aim

* Zero dependency: no dependency other than java standard library, and no jni extensions.
* Simple: keep code simple and clear.
* Modifiable when running: no need to reload for configuration update.
* Fast: performance is one of our main priorities.
* TCP Loadbalancer: we only support TCP for now.

## How to use

See docs for help.  
Questions about implementation detail are also welcome (in issues).

### Doc

* [how-to-use.md](https://github.com/wkgcass/vproxy/blob/master/doc/how-to-use.md): How to use config file and controllers.
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): Detailed command document.
* [lb-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/lb-example.md): An example about running a loadbalancer.
* [service-mesh-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-example.md): An example about vproxy service mesh.
* [docker-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/docker-example.md): An example about building and running vproxy in docker.
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): Something about the architecture.
* [service-mesh-protocol.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-protocol.md): The protocol which is used by vproxy service mesh impl.
* [extended-app.md](https://github.com/wkgcass/vproxy/blob/master/doc/extended-app.md): The usage of extended applications.
* [websocks.md](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md): The WebSocks Protocol.

## Contribute

Currently only `I` myself is working on this project. I would be very happy if you want to join :)
