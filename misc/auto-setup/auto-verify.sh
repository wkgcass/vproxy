#/bin/bash

set -o xtrace

export VBOX_VM="${VBOX_VM:-vproxy}"
export ROOT_PASS="${ROOT_PASS:-    }"

set -e

source ./func_xecho.sh
source ./func_v_exec.sh

xecho "!!!!!WARNING!!!!!"
xecho "This operation will mess your vm environment, and will delete or modify files, you may have to backup or snapshot the vm. Also please ensure current vm environment is clean otherwise verification may fail."
xecho "!!!!!WARNING!!!!!"
xecho "press Enter to continue"
set -e
read foo

RUN_VPROXY="${RUN_VPROXY:-1}"
RUN_VPCTL="${RUN_VPCTL:-1}"
RUN_K8S_VERIFY="${RUN_K8S_VERIFY:-1}"

if [ "$RUN_VPROXY" == "1" ]
then
	xecho "build vproxy"
	./exec-it 'cd vproxy && make all'

	xecho "verify switch functions"
	v_exec /bin/rm -rf /vproxy
	v_exec /bin/mkdir -p /vproxy
	v_exec /usr/bin/touch /vproxy/software
	v_exec /usr/bin/touch /vproxy/OpenJDK11U-jdk_x64_linux_hotspot_11.0.7_10.tar.gz
	v_exec /bin/bash -c  '/bin/ln -s "/root/`/bin/ls /root/ | /bin/grep jdk-11 | /usr/bin/head -n 1`" /vproxy/jdk-11.0.7+10'
	v_exec /bin/ln -s /root/vproxy /vproxy/vproxy
	./exec-it bash /vproxy/vproxy/misc/switch-test-init.sh

	xecho "verify docker plugin"
	v_exec /bin/mkdir -p /var/vproxy/docker-network-plugin/post-scripts
	v_exec /bin/mkdir -p /var/vproxy/auto-save
	v_exec /usr/bin/docker plugin enable vproxyio/docker-plugin:latest
	alpine_version=`v_exec /usr/bin/docker image list | grep alpine | awk '{print $2}'`
	v_exec /usr/bin/docker network create --ipv6 --driver=vproxyio/docker-plugin:latest --subnet=172.20.0.0/16 --subnet=2002:ac14:0000::/48 vproxy6
	v_exec /usr/bin/docker run --name=container-1 -d --rm --net=vproxy6 alpine:$alpine_version sh -c 'while true; do sleep 1; done'
	sleep 2
	ips=`v_exec /usr/bin/docker exec container-1 ip a | grep inet | grep -v '127.0.0.1' | grep -v '::1' | grep -v '\bfe80::' | awk '{print $2}' | cut -d '/' -f 1`
	for ip in $ips
	do
		v_exec /usr/bin/docker run --rm -i --net=vproxy6 alpine:$alpine_version ping -c 1 $ip
	done
	v_exec /usr/bin/docker kill container-1
	v_exec /usr/bin/docker network rm vproxy6
	v_exec /usr/bin/docker plugin disable vproxyio/docker-plugin:latest

fi

if [ "$RUN_VPCTL" == "1" ]
then
	xecho "build vpctl"
	./exec-it 'cd vpctl && make all'
	./exec-it 'docker image tag vproxyio/vproxy:latest 127.0.0.1:32000/vproxy:latest'
	./exec-it 'docker push 127.0.0.1:32000/vproxy:latest'
	./exec-it 'docker image tag wkgcass/vpctl:latest 127.0.0.1:32000/vpctl:latest'
	./exec-it 'docker push 127.0.0.1:32000/vpctl:latest'

	xecho "verify vpctl"
	./exec-it 'nohup /root/vproxy/vproxy-linux noLoadLast noSave sigIntDirectlyShutdown noStdIOController pidFile /root/vproxy-test.pid 2>&1 1>>/root/vproxy-test.log &'
	./exec-it 'cd /root/vpctl && ./vpctl_test'
	./exec-it 'kill -INT `cat /root/vproxy-test.pid`'

	xecho "verify controller"
	./exec-it "cd vpctl/misc && sed -i -E 's/wkgcass\/vproxy-base:latest/localhost:32000\/vproxy-base:latest/g' cr-example.yaml"
	./exec-it "cd vpctl/misc && sed -i -E 's/59.1.2.0\/24/0.0.0.0\/0/g' cr-example.yaml"
	./exec-it "cd vpctl/misc && sed -i -E 's/wkgcass\/vproxy:latest/localhost:32000\/vproxy:latest/g' k8s-vproxy.yaml"
	./exec-it "cd vpctl/misc && sed -i -E 's/wkgcass\/vpctl:latest/localhost:32000\/vpctl:latest/g' k8s-vproxy.yaml"
	./exec-it "cd vpctl/misc && sed -i -E 's/IfNotPresent/Always/g' cr-example.yaml"
	./exec-it "cd vpctl/misc && sed -i -E 's/IfNotPresent/Always/g' k8s-vproxy.yaml"
	./exec-it 'cd vpctl/misc && kubectl apply -f crd.yaml'
	./exec-it 'cd vpctl/misc && kubectl apply -f k8s-vproxy.yaml'
	./exec-it 'cd vpctl/misc && kubectl apply -f cr-example.yaml'

fi

if [ "$RUN_K8S_VERIFY" == "1" ]
then
	if [ "$RUN_VPCTL" == "1" ]
	then
		xecho "wait a few seconds before verifying (20s)"
		sleep 5
		xecho "wait a few seconds before verifying (15s)"
		sleep 5
		xecho "wait a few seconds before verifying (10s)"
		sleep 5
		xecho "wait a few seconds before verifying (5s)"
		sleep 5
	fi

	gateway_podname=`./exec-it kubectl -n vproxy-system get pod | grep 'vproxy-gateway' | grep 'Running' | awk '{print $1}'`
	set +o xtrace
	for i in {1..4}
	do
		xecho "curl"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl 127.0.0.1:80 2>/dev/null
		xecho "curl https"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl --http1.1 -k https://127.0.0.1:443 2>/dev/null
		xecho "socks5 example.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl --socks5 'socks5h://127.0.0.1:1080' http://example.com:80 2>/dev/null
		xecho "socks5 example2.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl --socks5 'socks5h://127.0.0.1:1080' http://example2.com:80 2>/dev/null
		xecho "curl example.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl -H 'Host: example.com' 127.0.0.1:80 2>/dev/null
		xecho "curl example2.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl -H 'Host: example2.com' 127.0.0.1:80 2>/dev/null
		xecho "curl https example.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl --http1.1 -k -H 'Host: example.com' https://127.0.0.1:443 2>/dev/null
		xecho "curl https example2.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- curl --http1.1 -k -H 'Host: example2.com' https://127.0.0.1:443 2>/dev/null
		xecho "dns example.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- dig @127.0.0.1 example.com
		xecho "dns example2.com"
		v_exec /snap/bin/microk8s.kubectl -n vproxy-system exec -it $gateway_podname --container vproxy -- dig @127.0.0.1 example2.com
	done
	set -o xtrace

fi

if [ "$RUN_VPCTL" == "1" ] && [ "$RUN_K8S_VERIFY" == "1" ]
then
	./exec-it "cd vpctl/misc && kubectl delete -f k8s-vproxy.yaml"
	./exec-it "cd vpctl/misc && kubectl delete -f cr-example.yaml"
	./exec-it "cd vpctl/misc && kubectl delete -f crd.yaml"
fi

xecho "all done"
