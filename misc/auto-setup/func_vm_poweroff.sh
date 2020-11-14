if [ -z "$XECHO" ]
then
	source ./func_xecho.sh
fi

function vm_poweroff() {
	set +e
	v_exec /sbin/poweroff
	xecho "waiting for vm '$VBOX_VM' to poweroff"
	while [ 1 ]
	do
		sleep 2s
		foo=`VBoxManage showvminfo $VBOX_VM | grep "State:" | grep "powered off"`
		if [ -z "$foo" ]
		then
			xecho "'$VBOX_VM' is still running"
			continue
		fi
		break
	done
}
