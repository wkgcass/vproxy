#!/bin/bash

if [ -z "$XECHO" ]
then
	source ./func_xecho.sh
fi

function vm_snap_remove() {
	VBOX_SNAP_TO_REMOVE="$1"
	IGNORE_WARNINGS="$2"

	snapshot_list=`VBoxManage snapshot "$VBOX_VM" list`
	set +e
	foo=`echo "$snapshot_list" | grep "Name: $VBOX_SNAP_TO_REMOVE\b"`
	if [ -z "$foo" ]
	then
		return
	fi
	if [ "$IGNORE_WARNINGS" == "0" ]
	then
		xecho "!!!!!WARNING!!!!!"
		xecho "Snapshot '$VBOX_SNAP_TO_REMOVE' already exists. This operation will delete the snapshot '$VBOX_SNAP_TO_REMOVE'"
		xecho "!!!!!WARNING!!!!!"
		xecho "press Enter to continue"
		set -e
		read foo
	fi
	set -e
	VBoxManage snapshot "$VBOX_VM" delete "$VBOX_SNAP_TO_REMOVE"
}
