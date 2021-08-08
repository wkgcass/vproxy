#!/bin/bash

set -e

function tryrun() {
	cmd="$@"

	set +e
	/bin/bash -c "$cmd"
	set -e
}

function trygrep() {
	cmd="$1"
	pattern="$2"

	set +e
	/bin/bash -c "$cmd" | grep "$pattern"
	set -e
}

function trygrep2() {
	cmd="$1"
	pattern="$2"

	set +e
	/bin/bash -c "$cmd" | grep "$pattern" | grep -v grep
	set -e
}

if [ $EUID -ne "0" ]
then
	echo "This script must be run as root" 
	exit 1
fi

cd /
mkdir -p vproxy
cd vproxy

res=`trygrep ls software`
if [ -z "$res" ]
then
	echo "installing software ..."
	tryrun apt-get -y update
	apt-get -y install build-essential git python iproute2 iputils-ping wget procps iptables
	touch software
else
	echo "software installed"
fi

# make netns
function makeNetNs() {
        name="$1"

        res=`trygrep "ip netns show" "\b$name\b"`
        if [ -z "$res" ]
        then
                echo "making netns $name"
                ip netns add "$name"
        else
                echo "netns $name exists"
        fi
	ip netns exec "$name" ip link set lo up
}
# switches
makeNetNs "sw1"
makeNetNs "sw2"
makeNetNs "sw3"
makeNetNs "sw-pub"
# the container for vxlan sock
makeNetNs "vm"
# the container to enumlate internet
makeNetNs "inet"
# the net ns for tap devices
makeNetNs "ns1"
makeNetNs "ns2"
makeNetNs "ns3"
makeNetNs "ns4"

# make veth
function makeVeth() {
        name="$1"
        ns1="$2"
        ns2="$3"
	ip1="$4"
	ip2="$5"

        res=`tryrun ip netns exec "$ns1" ip link show "$name-$ns1" 2>/dev/null`
        if [ -z "$res" ]
        then
		echo "$name-$ns1 is not in netns $ns1"
		res=`tryrun ip netns exec "$ns2" ip link show "$name-$ns2" 2>/dev/null`
		if [ -z "$res" ]
		then
			echo "$name-$ns2 is not in netns $ns2"
	                # check whether they exist under main namespace
			res=`tryrun ip link show "$name-$ns1" 2>/dev/null`
			if [ -z "$res" ]
			then
				echo "$name-$ns1 is not in main netns"
				res=`tryrun ip link show "$name-$ns2" 2>/dev/null`
				if [ -z "$res" ]
				then
					echo "$name-$ns2 is not in main netns"
		        	        echo "making veth $name in $ns1 and $ns2 ..."
        		        	ip link add "$name-$ns1" type veth peer name "$name-$ns2"
					# fall through
				else
					echo "[ERROR] $name-$ns1 is not in main ns, but $name-$ns2 is"
					exit 1
				fi
			else
				echo "$name-$ns1 is in main netns"
				res=`tryrun ip link show "$name-$ns2" 2>/dev/null`
				if [ -z "$res" ]
				then
					echo "[ERROR] veth $name-$ns1 is in main ns, but $name-$ns2 is not"
					exit 1
				else
					echo "$name-$ns2 is in main netns"
					echo "moving $name-$ns1 to netns $ns1, moving $name-$ns2 to netns $ns2 ..."
					# fall through
				fi
			fi
			ip link set "$name-$ns1" netns "$ns1"
			ip link set "$name-$ns2" netns "$ns2"
		else
			echo "[ERROR] veth $name-$ns1 is not in netns $n1, but $name-$ns2 is in netns $ns2"
			exit 1
		fi
        else
                echo "$name-$ns1 is in place"
		res=`tryrun ip netns exec "$ns2" ip link show "$name-$ns2" 2>/dev/null`
		if [ -z "$res" ]
		then
			echo "[ERROR] veth $name-$ns1 is in netns $ns1, but $name-$ns2 is not in netns $ns2"
			exit 1
		else
			echo "$name-$ns2 is in place"
		fi
        fi

	# assign ip1
	res=`trygrep "ip netns exec $ns1 ip addr show $name-$ns1" "$ip1"`
	if [ -z "$res" ]
	then
		echo "assigning ip $ip1 to $name-$ns1 ..."
		ip netns exec "$ns1" ip addr add "$ip1/24" dev "$name-$ns1"
	else
		echo "$name-$ns1 is already assigned with $ip1"
	fi
	# assign ip2
	res=`trygrep "ip netns exec $ns2 ip addr show $name-$ns2" "$ip2"`
	if [ -z "$res" ]
	then
		echo "assigning ip $ip2 to $name-$ns2 ..."
		ip netns exec "$ns2" ip addr add "$ip2/24" dev "$name-$ns2"
	else
		echo "$name-$ns2 is already assigned with $ip2"
	fi

	# up
	ip netns exec "$ns1" ip link set dev "$name-$ns1" up
	ip netns exec "$ns2" ip link set dev "$name-$ns2" up
}
makeVeth linkLeft  sw1 sw2    10.255.201.11 10.255.201.12
makeVeth linkRight sw1 sw3    10.255.202.21 10.255.202.23
makeVeth linkBot   sw1 sw-pub 10.255.203.31 10.255.203.39
makeVeth linkVm    sw1 vm     10.255.204.41 10.255.204.49
makeVeth linkInet  sw3 inet   10.255.205.53 10.255.205.59

# func to add ip
function addIp() {
	ns="$1"
	dev="$2"
	cidr="$3"
	v6="$4"

	if [ -z "$v6" ]
	then
		res=`trygrep "ip netns exec $ns ip addr show $dev" "$cidr"`
	else
		res=`trygrep "ip netns exec $ns ip -6 addr show $dev" "$cidr"`
	fi
	if [ -z "$res" ]
	then
		echo "adding ip to $dev in netns $ns ..."
		if [ -z "$v6" ]
		then
			ip netns exec "$ns" ip addr add "$cidr" dev "$dev"
		else
			ip netns exec "$ns" ip -6 addr add "$cidr" dev "$dev"
		fi
	else
		echo "ip is already added to $dev in netns $ns"
	fi
}

# func to add route
function addRoute() {
	ns="$1"
	net="$2"
	via="$3"
	dev="$4"

	if [ -z "$dev" ]
	then
		res=`trygrep "ip netns exec $ns ip route" "$net"`
	else
		res=`trygrep "ip netns exec $ns ip -6 route" "$net"`
	fi
	if [ -z "$res" ]
	then
		echo "adding route to $net in netns $ns ..."
		if [ -z "$dev" ]
		then
			ip netns exec "$ns" ip route add "$net" via "$via"
		else
			ip netns exec "$ns" ip -6 route add "$net" via "$via" dev "$dev"
		fi
	else
		echo "route to $net is already added in netns $ns"
	fi
}

# add ipv6 to sw3 and inet to emulate the netflow between internal and internet
addIp inet linkInet-inet fd00::cd3b/120 v6
addIp sw3 linkInet-sw3 fd00::cd35/120 v6

# add vxlan in netns vm
res=`tryrun ip netns exec "vm" ip link show "vxlan0"`
if [ -z "$res" ]
then
	echo "adding vxlan sock in netns vm ..."
	ip netns exec "vm" ip link add vxlan0 type vxlan id 3 remote 10.255.204.41 dstport 18472 srcport 18472 18473
else
	echo "vxlan sock already exists in netns vm"
fi
addIp vm vxlan0 172.16.3.6/24
addIp vm vxlan0 fd00::306/120 v6
ip netns exec "vm" ip link set dev vxlan0 up
addRoute vm default 172.16.3.254
addRoute vm default fd00::3fe vxlan0

# add br in ns sw-pub
echo "operating br in sw-pub ..."
res=`tryrun ip netns exec sw-pub ip link show br0 2>/dev/null`
if [ -z "$res" ]
then
	echo "creating br0 in sw-pub ..."
	ip netns exec "sw-pub" ip link add name br0 type bridge
else
	echo "br0 is already created in sw-pub"
fi
ip netns exec "sw-pub" ip link set dev br0 address 08:00:00:00:00:00
addIp sw-pub br0 172.16.1.101/24
addIp sw-pub br0 fd00::165/120 v6
ip netns exec "sw-pub" ip link set dev br0 up
addRoute sw-pub default 172.16.1.254
addRoute sw-pub default fd00::1fe br0

# add iptables in netns sw3 to masq packets to the internet
echo "operating iptables in sw3 ..."
ip netns exec "sw3" sysctl -w net.ipv4.ip_forward=1
ip netns exec "sw3" sysctl -w net.ipv6.conf.all.forwarding=1
ip netns exec "sw3" iptables -t nat -F
ip netns exec "sw3" ip6tables -t nat -F
ip netns exec "sw3" iptables -t nat -A POSTROUTING -s 172.16.1.0/24 ! -d 172.16.0.0/12 -o linkInet-sw3 -j SNAT --to 10.255.205.53
ip netns exec "sw3" iptables -t nat -A POSTROUTING -s 172.16.2.0/24 ! -d 172.16.0.0/12 -o linkInet-sw3 -j SNAT --to 10.255.205.53
ip netns exec "sw3" iptables -t nat -A POSTROUTING -s 172.16.3.0/24 ! -d 172.16.0.0/12 -o linkInet-sw3 -j SNAT --to 10.255.205.53
ip netns exec "sw3" ip6tables -t nat -A POSTROUTING -s fd00::100/120 ! -d fd00::/118 -o linkInet-sw3 -j SNAT --to fd00::cd35
ip netns exec "sw3" ip6tables -t nat -A POSTROUTING -s fd00::200/120 ! -d fd00::/118 -o linkInet-sw3 -j SNAT --to fd00::cd35
ip netns exec "sw3" ip6tables -t nat -A POSTROUTING -s fd00::300/120 ! -d fd00::/118 -o linkInet-sw3 -j SNAT --to fd00::cd35

# release files
echo "releasing files ..."

cat > /vproxy/sw1 <<EOL
add switch sw1 address 0.0.0.0:18472

add vpc 1 to switch sw1 v4network 172.16.1.0/24 v6network fd00::100/120
add vpc 2 to switch sw1 v4network 172.16.2.0/24 v6network fd00::200/120
add vpc 3 to switch sw1 v4network 172.16.3.0/24 v6network fd00::300/120

add switch sw2 to switch sw1 address 10.255.201.12:18472
add switch sw3 to switch sw1 address 10.255.202.23:18472

add user-client sw1x1 to switch sw1 address 10.255.203.39:18472 vni 1 password vproxy
add user-client sw1x2 to switch sw1 address 10.255.203.39:18472 vni 2 password vproxy
EOL

cat > /vproxy/sw2 <<EOL
add switch sw2 address 0.0.0.0:18472

add vpc 1 to switch sw2 v4network 172.16.1.0/24 v6network fd00::100/120
add vpc 2 to switch sw2 v4network 172.16.2.0/24 v6network fd00::200/120
add vpc 3 to switch sw2 v4network 172.16.3.0/24 v6network fd00::300/120

add switch sw1 to switch sw2 address 10.255.201.11:18472

add ip 172.16.1.254 to vpc 1 in switch sw2 mac 04:00:00:00:12:54
add ip fd00::1fe to vpc 1 in switch sw2 mac 06:00:00:00:12:54

add route route-to-2 to vpc 1 in switch sw2 network 172.16.2.0/24 vni 2
add route route-to-2-v6 to vpc 1 in switch sw2 network fd00::200/120 vni 2
add route route-to-3 to vpc 1 in switch sw2 network 172.16.3.0/24 vni 3
add route route-to-3-v6 to vpc 1 in switch sw2 network fd00::300/120 vni 3
add route route-to-1 to vpc 2 in switch sw2 network 172.16.1.0/24 vni 1
add route route-to-1-v6 to vpc 2 in switch sw2 network fd00::100/120 vni 1
add route route-to-3 to vpc 2 in switch sw2 network 172.16.3.0/24 vni 3
add route route-to-3-v6 to vpc 2 in switch sw2 network fd00::300/120 vni 3
add route route-to-1 to vpc 3 in switch sw2 network 172.16.1.0/24 vni 1
add route route-to-1-v6 to vpc 3 in switch sw2 network fd00::100/120 vni 1
add route route-to-2 to vpc 3 in switch sw2 network 172.16.2.0/24 vni 2
add route route-to-2-v6 to vpc 3 in switch sw2 network fd00::200/120 vni 2

add ip 172.16.2.192 to vpc 2 in switch sw2 mac 04:00:00:00:21:92
add ip fd00::2c0 to vpc 2 in switch sw2 mac 06:00:00:00:21:92
add ip 172.16.3.192 to vpc 3 in switch sw2 mac 04:00:00:00:31:92
add ip fd00::3c0 to vpc 3 in switch sw2 mac 06:00:00:00:31:92

add route internet to vpc 1 in switch sw2 network 0.0.0.0/0 vni 3
add route internet-v6 to vpc 1 in switch sw2 network ::/0 vni 3

add route internet-forward to vpc 3 in switch sw2 network 0.0.0.0/0 via 172.16.3.254
add route internet-forward-v6 to vpc 3 in switch sw2 network ::/0 via fd00::3fe

add tap tapns10 to switch sw2 vni 1 post-script /vproxy/tapns10.sh
add tap tapns20 to switch sw2 vni 2 post-script /vproxy/tapns20.sh
EOL

cat > /vproxy/sw3 <<EOL
add switch sw3 address 0.0.0.0:18472

add vpc 1 to switch sw3 v4network 172.16.1.0/24 v6network fd00::100/120
add vpc 2 to switch sw3 v4network 172.16.2.0/24 v6network fd00::200/120
add vpc 3 to switch sw3 v4network 172.16.3.0/24 v6network fd00::300/120

add switch sw1 to switch sw3 address 10.255.202.21:18472

add ip 172.16.3.254 to vpc 3 in switch sw3 mac 04:00:00:00:32:54
add ip fd00::3fe to vpc 3 in switch sw3 mac 06:00:00:00:32:54

add route route-to-2 to vpc 1 in switch sw3 network 172.16.2.0/24 vni 2
add route route-to-2-v6 to vpc 1 in switch sw3 network fd00::200/120 vni 2
add route route-to-3 to vpc 1 in switch sw3 network 172.16.3.0/24 vni 3
add route route-to-3-v6 to vpc 1 in switch sw3 network fd00::300/120 vni 3
add route route-to-1 to vpc 2 in switch sw3 network 172.16.1.0/24 vni 1
add route route-to-1-v6 to vpc 2 in switch sw3 network fd00::100/120 vni 1
add route route-to-3 to vpc 2 in switch sw3 network 172.16.3.0/24 vni 3
add route route-to-3-v6 to vpc 2 in switch sw3 network fd00::300/120 vni 3
add route route-to-1 to vpc 3 in switch sw3 network 172.16.1.0/24 vni 1
add route route-to-1-v6 to vpc 3 in switch sw3 network fd00::100/120 vni 1
add route route-to-2 to vpc 3 in switch sw3 network 172.16.2.0/24 vni 2
add route route-to-2-v6 to vpc 3 in switch sw3 network fd00::200/120 vni 2

add ip 172.16.1.193 to vpc 1 in switch sw3 mac 04:00:00:00:11:93
add ip fd00::1c1 to vpc 1 in switch sw3 mac 06:00:00:00:11:93
add ip 172.16.2.193 to vpc 2 in switch sw3 mac 04:00:00:00:21:93
add ip fd00::2c1 to vpc 2 in switch sw3 mac 06:00:00:00:21:93

add route internet to vpc 3 in switch sw3 network 0.0.0.0/0 via 172.16.3.5
add route internet-v6 to vpc 3 in switch sw3 network ::/0 via fd00::305

add tap tapns30 to switch sw3 vni 1 post-script /vproxy/tapns30.sh
add tap tapns40 to switch sw3 vni 2 post-script /vproxy/tapns40.sh
add tap tap5 to switch sw3 vni 3 post-script /vproxy/tap5.sh
EOL

cat > /vproxy/sw-pub <<EOL
add switch sw-pub address 0.0.0.0:18472

add vpc 101 to switch sw-pub v4network 172.16.1.0/24 v6network fd00::100/120
add vpc 102 to switch sw-pub v4network 172.16.2.0/24 v6network fd00::200/120

add user sw1x1 to switch sw-pub vni 101 password vproxy
add user sw1x2 to switch sw-pub vni 102 password vproxy

add ip 172.16.2.254 to vpc 102 in switch sw-pub mac 04:00:00:00:22:54
add ip fd00::2fe to vpc 102 in switch sw-pub mac 06:00:00:00:22:54

add route route-to-1 to vpc 102 in switch sw-pub network 172.16.1.0/24 vni 101
add route route-to-1-v6 to vpc 102 in switch sw-pub network fd00::100/120 vni 101
add route route-to-2 to vpc 101 in switch sw-pub network 172.16.2.0/24 vni 102
add route route-to-2-v6 to vpc 101 in switch sw-pub network fd00::200/120 vni 102

add ip 172.16.1.190 to vpc 101 in switch sw-pub mac 04:00:00:00:11:90
add ip fd00::1be to vpc 101 in switch sw-pub mac 06:00:00:00:11:90

add route route-to-3-1 to vpc 102 in switch sw-pub network 172.16.3.0/24 vni 101
add route route-to-3 to vpc 101 in switch sw-pub network 172.16.3.0/24 via 172.16.1.254
add route route-to-3-1-v6 to vpc 102 in switch sw-pub network fd00::300/120 vni 101
add route route-to-3-v6 to vpc 101 in switch sw-pub network fd00::300/120 via fd00::1fe

add route route-to-internet-1 to vpc 102 in switch sw-pub network 0.0.0.0/0 vni 101
add route route-to-internet to vpc 101 in switch sw-pub network 0.0.0.0/0 via 172.16.1.254
add route route-to-internet-1-v6 to vpc 102 in switch sw-pub network ::/0 vni 101
add route route-to-internet-v6 to vpc 101 in switch sw-pub network ::/0 via fd00::1fe

add tap tap101 to switch sw-pub vni 101 post-script /vproxy/tap101.sh
EOL

cat > /vproxy/tapns10.sh <<EOL
/usr/bin/env python \$NETNSUTIL add ns=ns1 sw=sw2 vni=1 addr=172.16.1.1/24 gate=172.16.1.254 v6addr=fd00::101/120 v6gate=fd00::1fe
EOL
chmod +x /vproxy/tapns10.sh

cat > /vproxy/tapns20.sh <<EOL
/usr/bin/env python \$NETNSUTIL add ns=ns2 sw=sw2 vni=2 addr=172.16.2.2/24 gate=172.16.2.254 v6addr=fd00::202/120 v6gate=fd00::2fe
EOL
chmod +x /vproxy/tapns20.sh

cat > /vproxy/tapns30.sh <<EOL
/usr/bin/env python \$NETNSUTIL add ns=ns3 sw=sw3 vni=1 addr=172.16.1.3/24 gate=172.16.1.254 v6addr=fd00::103/120 v6gate=fd00::1fe
EOL
chmod +x /vproxy/tapns30.sh

cat > /vproxy/tapns40.sh <<EOL
/usr/bin/env python \$NETNSUTIL add ns=ns4 sw=sw3 vni=2 addr=172.16.2.4/24 gate=172.16.2.254 v6addr=fd00::204/120 v6gate=fd00::2fe
EOL
chmod +x /vproxy/tapns40.sh

cat > /vproxy/tap5.sh <<EOL
ip addr add 172.16.3.5/24 dev tap5
ip -6 addr add fd00::305/120 dev tap5
ip link set tap5 up
ip route add 172.16.1.0/24 via 172.16.3.254
ip route add 172.16.2.0/24 via 172.16.3.254
ip -6 route add fd00::100/120 via fd00::3fe dev tap5
ip -6 route add fd00::200/120 via fd00::3fe dev tap5
EOL
chmod +x /vproxy/tap5.sh

cat > /vproxy/tap101.sh <<EOL
ip link set dev tap101 up
ip link set dev tap101 master br0
EOL
chmod +x /vproxy/tap101.sh

cat > /vproxy/ping.sh <<EOL
#!/bin/bash

ips="""
172.16.1.1    fd00::101
172.16.2.2    fd00::202
172.16.1.3    fd00::103
172.16.2.4    fd00::204
172.16.3.5    fd00::305
172.16.3.6    fd00::306
172.16.1.101  fd00::165

172.16.1.254  fd00::1fe
172.16.2.254  fd00::2fe
172.16.3.254  fd00::3fe

172.16.2.192  fd00::2c0
172.16.3.192  fd00::3c0
172.16.1.193  fd00::1c1
172.16.2.193  fd00::2c1
172.16.1.190  fd00::1be

10.255.205.59 fd00::cd3b
"""

ip neighbor flush all
ip -6 neighbor flush all

v6="6"

for ip in \$ips
do
	if [ "\$v6" == "6" ]
	then
		v6=""
	else
		v6="6"
	fi
	cmd="ping\$v6 -c 1 -W 1 \$ip"
	printf "\$ip\t"
	for i in {1..3}
	do
		\$cmd 1>/dev/null 2>/dev/null
		code="\$?"
		if [ "\$code" == "0" ]
		then
			echo "ok"
			break
		else
			if [ "\$i" == "3" ]
			then
				echo "fail"
			fi
		fi
	done
	sleep 0.2s
done
echo "finish"
EOL
chmod +x /vproxy/ping.sh

cat > /vproxy/ping-all.sh <<EOL
#!/bin/bash

list="vm sw3 sw-pub ns1 ns2 ns3 ns4"
for ns in \$list
do
	echo "run ping.sh in \$ns"
	ip netns exec "\$ns" /vproxy/ping.sh
done

echo "done"
EOL
chmod +x /vproxy/ping-all.sh

# runtime tar gz
res=`trygrep ls '^OpenJDK11U\-jdk_x64_linux_hotspot_11\.0\.7_10\.tar\.gz$'`
if [ -z "$res" ]
then
	echo "downloading adoptopenjdk11 ..."
	wget https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.7_10.tar.gz
else
	echo "adoptopenjdk11 is downloaded"
fi

# runtime
res=`trygrep ls '^jdk\-11\.0\.7+10$'`
if [ -z "$res" ]
then
	echo "extracting adoptopenjdk11 ..."
	tar zxf OpenJDK11U-jdk_x64_linux_hotspot_11.0.7_10.tar.gz
else
	echo "adoptopenjdk11 is ok"
fi

# java
export JAVA="/vproxy/jdk-11.0.7+10/bin/java"
export JAVA_HOME=/vproxy/jdk-11.0.7+10

# repo
res=`trygrep ls '^vproxy$'`
if [ -z "$res" ]
then
	echo "cloning vproxy src ..."
	git clone --depth=1 https://github.com/wkgcass/vproxy
else
	echo "vproxy src is ok"
fi

# compile
cd vproxy
echo "compiling ..."
make jar vfdposix

# vproxy
export JAR="/vproxy/vproxy/build/libs/vproxy.jar"
export NETNSUTIL="/vproxy/vproxy/misc/netnsutil.py"

# laucnh
function launch() {
	ns="$1"
	conf="$2"

	loadConf="load /vproxy/$conf"
	while [ 1 ]
	do
		res=`trygrep2 "ps aux" "$loadConf"`
		if [ -z "$res" ]
		then
			echo "process $conf not exists"
			break
		else
			pid=`echo "$res" | head -n 1 | awk '{print $2}'`
			kill "$pid"
			echo "wait for a few seconds for the old process to terminate ..."
			sleep 2s
		fi
	done

	cmd="ip netns exec $ns $JAVA -Dvfd=posix -Djava.library.path=/vproxy/vproxy/base/src/main/c -jar /vproxy/vproxy/build/libs/vproxy.jar $loadConf noSave sigIntDirectlyShutdown noStdIOController pidFile /vproxy/$conf.pid"
	echo "$cmd"
	nohup $cmd 2>&1 1>"/vproxy/$conf.log" &
	echo "process $conf: $!"
}

launch sw1 sw1
launch sw2 sw2
launch sw3 sw3
launch sw-pub sw-pub

sleep 1s
echo "done"

echo "wait for 10 seconds before ping-all.sh"
sleep 10s

/vproxy/ping-all.sh
