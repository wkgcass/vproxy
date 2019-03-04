#!/usr/bin/env bash

libdir=$1
resourcespath=$2
deppath=$3
name=$4

reflectconfig="reflectconfig.json"
applauncher="vproxy-websocks-agent-launcher-for-mac.sh"
appname="vproxy-websocks-agent"

function makeMacOS()
{
    args=$1
    imageName=$2

    native-image \
        -H:ReflectionConfigurationFiles="$resourcespath/$reflectconfig" \
        -D+A:UseDatagramChannel=false \
        -D+A:Graal=true \
        --no-server \
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
        -v "$resourcespath:/vproxy_res" \
        -v "$libdir:/vproxy_lib" \
        native-image-pack \
        \
        /graalvm/bin/native-image \
        -H:ReflectionConfigurationFiles="/vproxy_res/$reflectconfig" \
        -D+A:Graal=true \
        --no-server \
        $args \
        -jar "/vproxy_lib/$name.jar" \
        "/vproxy_lib/$imageName-linux"
}

# make MacOS image on the host system
makeMacOS "" "$name"

# make MacOS WebSocksAgent
makeMacOS "-D+A:EnableJs=true --language:js --enable-all-security-services -D+A:AppClass=WebSocksProxyAgent" "$name-WebSocksAgent"

# make MacOS WebSocksAgent without js support
makeMacOS "--enable-all-security-services -D+A:AppClass=WebSocksProxyAgent" "$name-WebSocksAgent-no-js"

# make MacOS WebSocksServer
makeMacOS "--enable-all-security-services -D+A:AppClass=WebSocksProxyServer" "$name-WebSocksServer"

# make Linux image on docker vm
makeLinux "" "$name"

# make Linux WebSocksAgent
makeLinux "-D+A:EnableJs=true --language:js --enable-all-security-services -D+A:AppClass=WebSocksProxyAgent" "$name-WebSocksAgent"

# make Linux WebSocksAgent without js support
makeLinux "--enable-all-security-services -D+A:AppClass=WebSocksProxyAgent" "$name-WebSocksAgent-no-js"

# make Linux WebSocksServer
makeLinux "--enable-all-security-services -D+A:AppClass=WebSocksProxyServer" "$name-WebSocksServer"

# clean the container(s)
docker rm `docker container list --all | awk '{print $1}' | tail -n +2`

# make MacOS WebSocksAgent app
# START
appcontentdir="$libdir/$appname.app/Contents/MacOS"
mkdir -p "$appcontentdir"
cp "$resourcespath/$applauncher" "$appcontentdir/$appname"
chmod +x "$appcontentdir/$appname"
cp "$libdir/$name-WebSocksAgent-macos" "$appcontentdir/"
cp "$deppath/libsunec.dylib" "$appcontentdir/"
# END

# remove intermediate .o files
cd "$libdir/"
rm *.o
