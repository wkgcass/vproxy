#!/bin/bash

set +e
cnt=`ls /x-etc/docker/.vproxy/mirror.json | wc -l`
set -e

if [ "$cnt" == "0" ]
then
  mkdir -p /x-etc/docker/.vproxy
  cp /default-mirror.json /x-etc/docker/.vproxy/mirror.json
fi

ulimit -l 65536
exec /vproxy-runtime/bin/java --enable-native-access=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -Xmx256m -Deploy=DockerNetworkPlugin -Dvfd=posix -Djava.library.path=/usr/lib/`uname -m`-linux-gnu -Dvproxy.MirrorConf=/x-etc/docker/.vproxy/mirror.json -jar /vproxy.jar allowSystemCommandInNonStdIOController noStdIOController sigIntDirectlyShutdown pidFile /var/run/docker/.vproxy/vproxy.pid
