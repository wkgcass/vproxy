#!/bin/bash

if [ -z "$XECHO" ]
then
	source ./func_xecho.sh
fi

function vm_rollback() {
	SNAPSHOT="$1"
	IGNORE_WARNING="$2"
	if [ "$IGNORE_WARNING" == "0" ]
	then
		xecho "!!!!!WARNING!!!!!"
		xecho "This operation will rollback your vm's current state, all changes after snapshot '$SNAPSHOT' will be lost!!!!!"
		xecho "!!!!!WARNING!!!!!"
		xecho "press Enter to continue"
		set -e
		read foo
	fi
	set -e
	VBoxManage snapshot "$VBOX_VM" restore "$SNAPSHOT"
}
