#!/bin/bash

exec /vproxy-runtime/bin/java --enable-native-access=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -Xmx128m -Deploy=Simple -Dvfd=posix -jar /vproxy.jar bind 'unix:///var/run/docker/plugins/vproxy.sock' backend 'unix:///var/run/docker/vproxy_network_plugin.sock' no-dns
