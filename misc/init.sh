#!/bin/bash

set -e

git submodule update --init --recursive

cd submodules/

MSQUIC_COMMIT="a2d88a82926d1d37306f9ef06990d4b62cd12ebe"
MSQUIC_VERSION="2.4.5"
if [[ ! -d ./msquic ]]; then
	git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
else
	refs=`cat msquic/.git/HEAD | awk '{print $2}'`
	commit=`cat "msquic/.git/$refs"`
	if [[ "$commit" != "$MSQUIC_COMMIT" ]]; then
		mv msquic/submodules ./msquic-submodules-backup
		rm -rf ./msquic
		git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
		rm -rf msquic/submodules
		mv ./msquic-submodules-backup msquic/submodules
	fi
fi

cd msquic/
git submodule update --init --recursive --recommend-shallow
