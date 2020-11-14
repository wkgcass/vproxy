function v_exec() {
	VBoxManage guestcontrol "$VBOX_VM" --username=root --password="$ROOT_PASS" run -- "$@"
}
