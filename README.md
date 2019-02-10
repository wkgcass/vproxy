# vproxy

## Intro

VProxy is a zero-dependency TCP Loadbalancer based on Java NIO. The project only requires Java 8 to run.

Clone it, javac it, then everything is ready for running.

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

* [how-to-use.md](https://github.com/wkgcass/vproxy/blob/master/doc/how-to-use.md) How to use config file and controllers.
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): Detailed command document.
* [lb-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/lb-example.md): An example about running a loadbalancer.
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): Something about the architecture.
* [service-mesh-protocol.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-protocol.md): The protocol which is used by vproxy service mesh impl.

## Contribute

Currently only `I` myself is working on this project. I would be very happy if you want to join :)
