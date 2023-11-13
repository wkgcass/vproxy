#!/bin/bash

LINE="==================================="
function echo_line() {
    echo $LINE
    echo $1
    echo $LINE
}

set -e

chkdir=`pwd`
echo_line "checkout directory is $chkdir"

echo_line "ensure compile docker image"
docker pull vproxyio/compile:latest

echo_line "cd into build directory: $BUILDDIR"
cd $BUILDDIR
if [[ ! -d ./vproxy ]]; then
	echo_line "vproxy directory doesn't exist, run git clone"
	git clone https://github.com/wkgcass/vproxy
else
	echo_line "vproxy directory exists"
fi

echo_line "cd into vproxy and clean the directory"
cd ./vproxy
make clean

git reset HEAD --hard
git clean -f -d
make clean

echo_line "pulling latest code"
git pull
make clean

echo_line "check whether need to apply patch"
set +e
check_patch=`ssh teamcity ls ./dustbin/vproxy/patch`
echo_line "check_patch result is: $check_patch"
set -e
if [[ "$check_patch" != "" ]]; then
    echo_line "applying patch"
    ssh teamcity cat ./dustbin/vproxy/patch 2>/dev/null | git apply
else
    echo_line "no patch exists"
fi

echo_line "init the project"
make init

echo_line "building ..."
make clean native

echo_line "copy artifacts to checkout directory"
set +e
set -x
cp `find . -name '*.so'` "$chkdir"
cp `find . -name '*.dylib'` "$chkdir"
cp `find . -name '*.dll'` "$chkdir"
exit 0
