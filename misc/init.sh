#!/bin/bash

set -e

git submodule update --init --recursive

cd submodules/

MSQUIC_COMMIT="2a34f44107298d1e8723825b65dac4761ca6cafa"
MSQUIC_VERSION="2.4.5"
if [[ ! -d ./msquic ]]; then
	git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
else
	refs=`cat msquic/.git/HEAD | awk '{print $2}'`
	commit=`cat "msquic/.git/$refs"`
	if [[ "$commit" != "$MSQUIC_COMMIT" ]]; then
		rm -rf ./msquic
		git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
	fi
fi

cd msquic/
git submodule update --init --recursive --recommend-shallow
