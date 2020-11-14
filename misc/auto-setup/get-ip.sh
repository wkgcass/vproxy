#!/bin/bash

VBOX_VM="${VBOX_VM:-vproxy}"
ROOT_PASS="${ROOT_PASS:-    }"

ips=`VBoxManage guestcontrol "$VBOX_VM" --username=root --password="$ROOT_PASS" run -- /sbin/ip a | grep 'inet' | grep -v 'inet6' | awk '{print $2}' | cut -d '/' -f 1 | grep -v '^127\.'`

ip=""

for i in $ips
do
	foo=`ssh -o LogLevel=ERROR -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ./key.rsa2048.pem "root@$i" -- uptime`
	if [ -z "$foo" ]
	then
		continue
	fi
	ip="$i"
	break
done

if [ -z "$ip" ]
then
	echo "no ip found for ssh" >&2
	exit 1
fi

echo "$ip"
exit 0
