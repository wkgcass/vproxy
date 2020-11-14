#!/bin/bash

if [ -z "$XECHO" ]
then
	source ./func_xecho.sh
fi

function vm_start() {
	VBoxManage startvm "$VBOX_VM" --type headless
	set +e
	xecho "waiting for vm '$VBOX_VM' to launch..."
	while [ 1 ]
	do
		sleep 2s
		foo=`VBoxManage showvminfo "$VBOX_VM" | grep "State:" | grep "running"`
		if [ -z "$foo" ]
		then
			xecho "vm '$VBOX_VM' is not running yet..."
			continue
		fi
		foo=`v_exec /usr/bin/uptime`
		if [ -z "$foo" ]
		then
			xecho "vm '$VBOX_VM' is not up yet..."
			continue
		fi
		break
	done
}
