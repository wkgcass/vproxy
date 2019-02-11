#!/usr/bin/env bash

libdir=$1
reflectconfigpath=$2
reflectconfig=$3
name=$4

# make MacOS image on the host system
native-image \
    -H:ReflectionConfigurationFiles="$reflectconfigpath/$reflectconfig" \
    -D+A:UseDatagramChannel=false \
    -jar "$libdir/$name.jar" \
    "$libdir/$name-macos"

# make Linux image on docker vm
docker \
    run \
    -v "$reflectconfigpath:/vproxy_res" \
    -v "$libdir:/vproxy_lib" \
    native-image-pack \
    \
    /graalvm/bin/native-image \
    -H:ReflectionConfigurationFiles="/vproxy_res/$reflectconfig" \
    -D+A:UseDatagramChannel=false \
    -jar "/vproxy_lib/$name.jar" \
    "/vproxy_lib/$name-linux"

# clean the container(s)
docker rm `docker container list --all | awk '{print $1}' | tail -n +2`
