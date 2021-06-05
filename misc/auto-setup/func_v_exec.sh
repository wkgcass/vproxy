function v_exec() {
	VBoxManage guestcontrol "$VBOX_VM" --username=root --password="$ROOT_PASS" run --putenv LC_ALL=C.UTF-8 --putenv LANG=C.UTF-8 -- "$@"
}
