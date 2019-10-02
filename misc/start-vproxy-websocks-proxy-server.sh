#!/bin/bash

runtime="vproxy-runtime-linux"
kcptunPort="8443"
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
	echo "Generating pkcs12 file"
	rm -f cert.pem
	rm -f key.pem
	rm -f certkey.pem
	rm -f certkey.p12
	openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 36500 -subj "/C=GB/ST=London/L=London/O=Global Security/OU=IT Department/CN=example.com" 2>/dev/null
	cat cert.pem > certkey.pem
	cat key.pem >> certkey.pem
	echo "Input the p12 file password, which will be used later"
	openssl pkcs12 -export -in certkey.pem -out certkey.p12
	ret=$?
	if [ $ret -ne 0 ]
	then
		rm -f certkey.p12
		echo "Failed!"
		exit $ret
	fi
else
	echo "PKCS12 file exists"
fi

check=`ls kcptun 2>/dev/null`

if [ -z "$check" ]
then
	echo "Creating kcptun directory"
	mkdir kcptun
else
	echo "kcptun directory exists"
fi

cd kcptun

check=`ls server_linux_amd64 2>/dev/null`

if [ -z "$check" ]
then
	echo "Getting kcptun"
	rm -f client_linux_amd64
	rm -f server_linux_amd64
	rm -f kcptun-linux-amd64-20190611.tar.gz
	wget -q https://github.com/xtaci/kcptun/releases/download/v20190611/kcptun-linux-amd64-20190611.tar.gz
	tar zxf kcptun-linux-amd64-20190611.tar.gz
else
	echo "kcptun exists"
fi

cd ../

# p12 password
read -p "PKCS12 file password: " pswd
# user
read -p "your username: " user
# pass
read -p "your password: " pass

check=`ps aux | grep server_linux_amd64 | grep -v grep`
kcptunpid=""

if [ -z "$check" ]
then
	echo "checking udp port :$kcptunPort"
	udpPortRes=`lsof -P -n -i ":$kcptunPort" | grep ' UDP '`
	if [ -z "$kcptunPortRes" ]
	then
		echo "launching kcptun"
		kcptun/server_linux_amd64 -t "127.0.0.1:$listenPort" -l ":$kcptunPort" -mode fast3 -nocomp -dscp 46 &
		kcptunpid=`echo $!`
		echo "kcptun started on pid $kcptunpid"
	else
		echo "udp port :$kcptunPort already bond"
		exit 1
	fi
else
	echo "kcptun already running"
fi

version=`"$runtime/bin/java" -jar vproxy.jar version`

echo "current vproxy version: $version"

echo "launching..."
echo "README: It's recommended to run this application inside tmux."

"$runtime/bin/java" -Deploy=WebSocksProxyServer -jar vproxy.jar listen "$listenPort" ssl pkcs12 certkey.p12 pkcs12pswd "$pswd" auth "$user:$pass"

# kill kcptun after java exits
if [[ ! -z "$kcptunpid" ]]
then
	echo "kill kcptun $kcptunpid"
	kill $kcptunpid
fi
