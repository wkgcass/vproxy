#!/bin/bash

runtime="vproxy-runtime-linux"
listenPort="443"

echo "check and install missing softwares"

# try to install softwares using apt-get
apt-get install -qq curl wget lsof procps 1>/dev/null 2>/dev/null

# try to install softwares using yum
yum install -y curl wget lsof procps 1>/dev/null 2>/dev/null

check=`ls vproxy 2>/dev/null`

if [ -z "$check" ]
then
	echo "Making directory: vproxy"
	mkdir vproxy
else
	echo "vproxy directory exists"
fi

cd vproxy

check=`ls "$runtime.tar.gz" 2>/dev/null`

if [ -z "$check" ]
then
	echo "Getting $runtime.tar.gz"
	wget -q "https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/$runtime.tar.gz"
else
	echo "runtime tar file exists"
fi

check=`ls "$runtime" 2>/dev/null`

if [ -z "$check" ]
then
	echo "Extracting $runtime.tar.gz"
	tar zxf "$runtime.tar.gz"
else
	echo "runtime tar extracted"
fi

version=`curl https://raw.githubusercontent.com/wkgcass/vproxy/master/src/main/java/vproxy/app/Application.java 2>/dev/null | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2`
jar_name="vproxy-$version.jar"

check=`ls vproxy.jar 2>/dev/null`

if [ -z "$check" ]
then
	echo "Getting $jar_name"
	rm -f "$jar_name"
	rm -f vproxy.jar
	wget -q "https://github.com/wkgcass/vproxy/releases/download/$version/$jar_name"
	mv "$jar_name" vproxy.jar
else
	echo "vproxy.jar exists"
fi

check=`ls certkey.p12 2>/dev/null`

if [ -z "$check" ]
then
	echo "Generating certificate files"
	rm -f cert.pem
	rm -f key.pem
	openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 36500 -subj "/C=GB/ST=London/L=London/O=Global Security/OU=IT Department/CN=example.com" 2>/dev/null
else
	echo "PKCS12 file exists"
fi

cd ../

# user
read -p "your username: " user
# pass
read -p "your password: " pass

version=`"$runtime/bin/java" -jar vproxy.jar version`

echo "current vproxy version: $version"

# detect old process
check=`ps aux | grep 'WebSocksProxyServer' | grep -v grep | awk '{print $2}'`

if [ -z "$check" ]
then
	echo "Terminating old WebSocksProxyServer process: $check"
	kill "$check"
fi

echo "launching..."

nohup "$runtime/bin/java" -Deploy=WebSocksProxyServer -jar vproxy.jar \
	listen "$listenPort" \
	ssl certpem cert.pem keypem key.pem \
	auth "$user:$pass" \
	kcp \
	2>&1 >> vproxy.log &

echo ""
