function v_copyto() {
	guest_dir="$1"
	host_src="$2"
	VBoxManage guestcontrol "$VBOX_VM" --username=root --password="$ROOT_PASS" copyto -R --target-directory "$guest_dir" "$host_src"
}
