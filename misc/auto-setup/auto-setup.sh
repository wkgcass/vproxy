#!/bin/bash

set -o xtrace
source ./func_xecho.sh

export VBOX_VM="${VBOX_VM:-vproxy}"
export VBOX_SNAP_INSTALLED="${VBOX_SNAP_INSTALLED:-installed}"
export VBOX_SNAP_INIT="${VBOX_SNAP_INIT:-init}"
export ROOT_PASS="${ROOT_PASS:-    }"
export ROOT_PASS_DESCR="${ROOT_PASS_DESCR:-(4 spaces)}"

export VBOX_SNAP_BASIC="${VBOX_SNAP_BASIC:-basic}"
export VBOX_SNAP_DOCKER="${VBOX_SNAP_DOCKER:-docker}"
export VBOX_SNAP_K8S="${VBOX_SNAP_K8S:-k8s}"
export VBOX_SNAP_JAVA="${VBOX_SNAP_JAVA:-java}"
export VBOX_SNAP_GO="${VBOX_SNAP_GO:-go}"
export VBOX_SNAP_GRAAL="${VBOX_SNAP_GRAAL:-graal}"
export VBOX_SNAP_GRADLE="${VBOX_SNAP_GRADLE:-gradle}"
export VBOX_SNAP_IMAGES="${VBOX_SNAP_IMAGES:-images}"

vm_list=`VBoxManage list vms`
foo=`echo "$vm_list" | grep "\"$VBOX_VM\""`

if [ -z "$foo" ]
then
	xecho "cannot find virtual machine '$VBOX_VM'"
	xecho "please take the following steps:"
	xecho "  1. create a vm named '$VBOX_VM'"
	xecho "  2. ensure enough cpu/memory/disk (required by native-image and k8s)"
	xecho "  3. set nic1 to use Host-Only (for host access)"
	xecho "  4. set nic2 to use Host-Only and use chipset to 82545EM (for DPDK)"
	xecho "  5. set nic3 to use NAT (for external network access)"
	exit 1
fi

snapshot_list=`VBoxManage snapshot "$VBOX_VM" list`
foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_INSTALLED\b"`
if [ -z "$foo" ]
then
	xecho "cannot find snapshot '$VBOX_SNAP_INSTALLED'"
	xecho "please take the following steps:"
	xecho "  1. install Ubuntu server 18.04 in vm '$VBOX_VM'"
	xecho "       https://ubuntu.com/download/server"
	xecho "  2. enable root with password '$ROOT_PASS' $ROOT_PASS_DESCR"
	xecho "       https://www.computernetworkingnotes.com/linux-tutorials/how-to-enable-and-disable-root-login-in-ubuntu.html"
	xecho "  3. install vbox guest additions"
	xecho "       https://linuxize.com/post/how-to-install-virtualbox-guest-additions-in-ubuntu/"
	xecho "  4. reboot the vm multiple times to make sure everything is ok"
	xecho "  5. poweroff the vm"
	xecho "  6. take a snapshot with name '$VBOX_SNAP_INSTALLED'"
	exit 1
fi

foo=`VBoxManage showvminfo "$VBOX_VM" | grep "State:" | grep "powered off"`
if [ -z "$foo" ]
then
	xecho "vm '$VBOX_VM' is running, please poweroff the vm before proceeding"
	exit 1
fi

set -e
source ./func_v_exec.sh
source ./func_v_copyto.sh
source ./func_vm_start.sh
source ./func_vm_poweroff.sh
source ./func_vm_rollback.sh

function ssh_exec() {
	./exec-it "$@"
}

set +e
foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_INIT\b"`
if [ -z "$foo" ]
then
	xecho "snapshot '$VBOX_SNAP_INIT' not found"
	xecho "begin to initiate vm '$VBOX_VM'"

	rollbacked=0
	last_snap="$VBOX_SNAP_INSTALLED"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_BASIC\b"`
	if [ -z "$foo" ]
	then
		xecho "install and configure basic resources"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start

		xecho "install softwares"
		set -e
		v_exec /usr/bin/apt-get install -y git openssh-server lsof procps net-tools iproute2 tcpdump strace curl iputils-ping wget build-essential autoconf netcat binutils socat dnsutils vim python python3 libnuma-dev libssl-dev gawk zlib1g-dev

		xecho "configure ssh"
		v_exec /bin/sed '/PermitRootLogin/d' /etc/ssh/sshd_config > /tmp/sshd_config
		echo "PermitRootLogin prohibit-password" >> /tmp/sshd_config
		v_copyto /etc/ssh/ /tmp/sshd_config
		v_exec /bin/mkdir -p /root/.ssh
		v_copyto /root/.ssh/ ./authorized_keys
		v_exec /bin/chmod 400 /root/.ssh/authorized_keys
		v_exec /usr/sbin/service ssh restart
		rm /tmp/sshd_config

		xecho "configure system"
		v_exec /bin/sed '/DefaultTimeoutStopSec/d' /etc/systemd/system.conf > /tmp/system.conf
		echo "DefaultTimeoutStopSec=10s" >> /tmp/system.conf
		v_copyto /etc/systemd/ /tmp/system.conf
		v_exec /bin/systemctl daemon-reload
		rm /tmp/system.conf

		xecho "configure env"
		v_exec /bin/bash -c '/bin/echo "export PATH=\$PATH:/snap/bin" >> /root/.bashrc'

		xecho "basic resources installed and configured, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_BASIC' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_BASIC"
	fi
	last_snap="$VBOX_SNAP_BASIC"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_DOCKER\b"`
	if [ -z "$foo" ]
	then
		xecho "install docker"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		v_exec /usr/bin/apt-get remove -y docker docker-engine docker.io containerd runc
		v_exec /bin/rm -rf /var/lib/docker
		v_exec /usr/bin/apt-get install -y apt-transport-https ca-certificates curl gnupg-agent software-properties-common
		v_exec /bin/bash -c '/usr/bin/curl -fsSL https://download.docker.com/linux/ubuntu/gpg | /usr/bin/apt-key add -'
		v_exec /bin/bash -c '/usr/bin/add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"'
		v_exec /usr/bin/apt-get update -y
		v_exec /usr/bin/apt-get install -y docker-ce docker-ce-cli containerd.io

		xecho "docker installed, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_DOCKER' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_DOCKER"
	fi
	last_snap="$VBOX_SNAP_DOCKER"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_K8S\b"`
	if [ -z "$foo" ]
	then
		xecho "setup k8s"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		ssh_exec /usr/bin/snap install microk8s --classic
		v_exec /bin/bash -c '/bin/echo "alias kubectl=microk8s.kubectl" >> /root/.bashrc'
		v_exec /snap/bin/microk8s.enable registry

		xecho "k8s installed, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_K8S' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_K8S"
	fi
	last_snap="$VBOX_SNAP_K8S"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_JAVA\b"`
	if [ -z "$foo" ]
	then
		xecho "setup jdk"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		ssh_exec /usr/bin/wget https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.9%2B11.1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.9_11.tar.gz
		ssh_exec /bin/tar zxf OpenJDK11U-jdk_x64_linux_hotspot_11.0.9_11.tar.gz
		v_exec /bin/bash -c '/bin/echo "export PATH=\$PATH:/root/jdk-11.0.9+11/bin" >> /root/.bashrc'
		v_exec /bin/bash -c '/bin/echo "export JAVA_HOME=/root/jdk-11.0.9+11" >> /root/.bashrc'

		xecho "java installed, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_JAVA' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_JAVA"
	fi
	last_snap="$VBOX_SNAP_JAVA"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_GO\b"`
	if [ -z "$foo" ]
	then
		xecho "setup go"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		ssh_exec /usr/bin/wget https://golang.org/dl/go1.15.4.linux-amd64.tar.gz
		ssh_exec /bin/tar zxf go1.15.4.linux-amd64.tar.gz
		v_exec /bin/bash -c '/bin/echo "export PATH=\$PATH:/root/go/bin" >> /root/.bashrc'

		xecho "go installed, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_GO' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_GO"
	fi
	last_snap="$VBOX_SNAP_GO"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_GRAAL\b"`
	if [ -z "$foo" ]
	then
		xecho "setup graal"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		ssh_exec /usr/bin/wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-20.2.0/graalvm-ce-java11-linux-amd64-20.2.0.tar.gz
		ssh_exec /bin/tar zxf graalvm-ce-java11-linux-amd64-20.2.0.tar.gz
		ssh_exec /root/graalvm-ce-java11-20.2.0/bin/gu install native-image
		v_exec /bin/bash -c '/bin/echo "export PATH=\$PATH:/root/graalvm-ce-java11-20.2.0/bin" >> /root/.bashrc'

		xecho "graal installed, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_GRAAL' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_GRAAL"
	fi
	last_snap="$VBOX_SNAP_GRAAL"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_IMAGES\b"`
	if [ -z "$foo" ]
	then
		xecho "pull docker images"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		ssh_exec docker pull alpine:3.9.2
		ssh_exec docker pull ubuntu:18.04
		v_exec /bin/mkdir -p /root/docker-vproxy-base
		v_copyto /root/docker-vproxy-base ./vproxy-base/Dockerfile
		v_copyto /root/docker-vproxy-base ./vproxy-base/sources.list
		ssh_exec docker build --no-cache -t vproxy-base:latest ./docker-vproxy-base
		v_exec /usr/bin/docker image tag vproxy-base:latest 127.0.0.1:32000/vproxy-base:latest
		ssh_exec docker push 127.0.0.1:32000/vproxy-base:latest

		xecho "images pulled, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_IMAGES' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_IMAGES"
	fi
	last_snap="$VBOX_SNAP_IMAGES"

	## ==============
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_GRADLE\b"`
	if [ -z "$foo" ]
	then
		xecho "setup gradle"
		vm_rollback "$last_snap" "$rollbacked"
		rollbacked=1

		vm_start
		set -e
		v_exec /bin/mkdir -p /root/gradle-init/gradle
		v_copyto /root/gradle-init/ ./gradlew
		v_copyto /root/gradle-init/gradle ./gradle
		v_exec /bin/chmod +x /root/gradle-init/gradlew
		ssh_exec 'cd /root/gradle-init/ && ./gradlew --version'

		xecho "gradle downloaded, shutdown the vm '$VBOX_VM'"
		vm_poweroff
		xecho "take snapshot '$VBOX_SNAP_GRDLE' for vm '$VBOX_VM'"
		set -e
		VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_GRADLE"
	fi
	last_snap="$VBOX_SNAP_GRADLE"

	## ==============
	vm_rollback "$last_snap" "$rollbacked"
	rollbacked=1
	xecho "take snapshot '$VBOX_SNAP_INIT' for vm $VBOX_VM"
	set -e
	VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_INIT"
	last_snap="$VBOX_SNAP_INIT"

	## ==============
	xecho "test the setup"
	vm_start
	xecho "wait for a few seconds for the services to launch... (15s)"
	sleep 5s
	xecho "wait for a few seconds for the services to launch... (10s)"
	sleep 5s
	xecho "wait for a few seconds for the services to launch... (5s)"
	sleep 5s
	set -e
	ssh_exec docker image list
	ssh_exec kubectl get nodes
	ssh_exec java -version
	ssh_exec go version
	ssh_exec native-image --version
	ssh_exec 'ls ~/.gradle'
	xecho "the setup seems ok, shutting down vm '$VBOX_VM'"
	vm_poweroff
	vm_rollback "$last_snap" "$rollbacked"
fi

set -e

xecho "all done"
