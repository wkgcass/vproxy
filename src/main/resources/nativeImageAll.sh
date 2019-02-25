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

# make MacOS WebSocks5Agent
makeMacOS "--enable-all-security-services -D+A:AppClass=WebSocks5ProxyAgent" "$name-WebSocks5Agent"

# make MacOS WebSocks5Server
makeMacOS "-D+A:AppClass=WebSocks5ProxyServer" "$name-WebSocks5Server"

# make Linux image on docker vm
makeLinux "" "$name"

# make MacOS WebSocks5Agent
makeLinux "--enable-all-security-services -D+A:AppClass=WebSocks5ProxyAgent" "$name-WebSocks5Agent"

# make MacOS WebSocks5Server
makeLinux "-D+A:AppClass=WebSocks5ProxyServer" "$name-WebSocks5Server"

# clean the container(s)
docker rm `docker container list --all | awk '{print $1}' | tail -n +2`
