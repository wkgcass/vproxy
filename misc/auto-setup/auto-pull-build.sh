#!/bin/bash

set -o xtrace
set -e

source ./func_xecho.sh

xecho "ensure that the vm is initiated ..."
./auto-setup.sh

export VBOX_VM="${VBOX_VM:-vproxy}"
export ROOT_PASS="${ROOT_PASS:-    }"
export VBOX_SNAP_INIT="${VBOX_SNAP_INIT:-init}"
export VBOX_SNAP_BUILD="${VBOX_SNAP_BUILD:-build}"
export VPROXY_BRANCH="${VPROXY_BRANCH:-master}"
export VPCTL_BRANCH="${VPCTL_BRANCH:-master}"

set -e
source ./func_v_exec.sh
source ./func_vm_start.sh
source ./func_vm_poweroff.sh
source ./func_vm_rollback.sh
source ./func_vm_snap_remove.sh

vm_snap_remove "$VBOX_SNAP_BUILD" 0
vm_rollback "$VBOX_SNAP_INIT" 0
vm_start

set -e
# clone vproxy
./exec-it git clone https://github.com/wkgcass/vproxy.git --depth=1 --branch="$VPROXY_BRANCH"
./exec-it "cd vproxy && make clean && make"
./exec-it git clone https://github.com/vproxy-tools/vpctl.git --depth=1 --branch="$VPCTL_BRANCH"
./exec-it "cd vpctl && make clean && make"

vm_poweroff
set -e
VBoxManage snapshot "$VBOX_VM" take "$VBOX_SNAP_BUILD"

xecho "all done"
