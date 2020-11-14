#!/bin/bash

set -o xtrace
set -e

export VBOX_VM="${VBOX_VM:-vproxy}"
export ROOT_PASS="${ROOT_PASS:-    }"
export VPROXY_PATH="${VPROXY_PATH:-`pwd | xargs dirname | xargs dirname`}"
VPROXY_NAME=`basename "$VPROXY_PATH"`
VPROXY_TAR=`dirname "$VPROXY_PATH"`/"$VPROXY_NAME.tar.gz"
export VPCTL_PATH="${VPCTL_PATH:-`pwd | xargs dirname | xargs dirname | xargs dirname`/vpctl}"
VPCTL_NAME=`basename "$VPCTL_PATH"`
VPCTL_TAR=`dirname "$VPCTL_PATH"`/"$VPCTL_NAME.tar.gz"

set -e
source ./func_xecho.sh
source ./func_v_exec.sh
source ./func_v_copyto.sh

xecho "!!!!!WARNING!!!!!"
xecho "This operation will remove all your current projects in the vm. Your changes will be lost."
xecho "!!!!!WARNING!!!!!"
xecho "press Enter to continue"
set -e
read foo

set -e
./exec-it uptime
v_exec /usr/bin/uptime

set -e
./tar.sh "$VPROXY_NAME.tar.gz" "$VPROXY_PATH"
./exec-it rm -rf /root/vproxy
./exec-it mkdir -p /root/vproxy
v_copyto /root/ `dirname "$VPROXY_PATH"`/"$VPROXY_NAME.tar.gz"
./exec-it tar zxf "$VPROXY_NAME.tar.gz"
./exec-it "cd vproxy && make clean && make"

set -e
./tar.sh "$VPCTL_NAME.tar.gz" "$VPCTL_PATH"
./exec-it rm -rf /root/vpctl
./exec-it mkdir -p /root/vpctl
v_copyto /root/ `dirname "$VPCTL_PATH"`/"$VPCTL_NAME.tar.gz"
./exec-it tar zxf "$VPCTL_NAME.tar.gz"
./exec-it "cd vpctl && make clean && make"

xecho "all done"
