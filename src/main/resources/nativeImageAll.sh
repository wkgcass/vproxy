#!/usr/bin/env bash

libdir=$1
reflectconfigpath=$2
reflectconfig=$3
name=$4

function makeMacOS()
{
    args=$1
    imageName=$2

    native-image \
        -H:ReflectionConfigurationFiles="$reflectconfigpath/$reflectconfig" \
        -D+A:UseDatagramChannel=false \
        $args \
        -jar "$libdir/$name.jar" \
        "$libdir/$imageName-macos"
}

function makeLinux()
{
    args=$1
    imageName=$2

    docker \
        run \
        -v "$reflectconfigpath:/vproxy_res" \
        -v "$libdir:/vproxy_lib" \
        native-image-pack \
        \
        /graalvm/bin/native-image \
        -H:ReflectionConfigurationFiles="/vproxy_res/$reflectconfig" \
        -D+A:UseDatagramChannel=false \
        $args \
        -jar "/vproxy_lib/$name.jar" \
        "/vproxy_lib/$imageName-linux"
}

# make MacOS image on the host system
makeMacOS "" "$name"

# make MacOS WebSocksAgent
makeMacOS "--enable-all-security-services -D+A:AppClass=WebSocksProxyAgent" "$name-WebSocksAgent"

# make MacOS WebSocksServer
makeMacOS "-D+A:AppClass=WebSocksProxyServer" "$name-WebSocksServer"

# make Linux image on docker vm
makeLinux "" "$name"

# make MacOS WebSocksAgent
makeLinux "--enable-all-security-services -D+A:AppClass=WebSocksProxyAgent" "$name-WebSocksAgent"

# make MacOS WebSocksServer
makeLinux "-D+A:AppClass=WebSocksProxyServer" "$name-WebSocksServer"

# clean the container(s)
docker rm `docker container list --all | awk '{print $1}' | tail -n +2`
