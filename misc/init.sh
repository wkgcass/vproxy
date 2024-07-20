#!/bin/bash

set -e

git submodule update --init --recursive

cd submodules/

MSQUIC_VERSION="2.2.4"
if [[ ! -d ./msquic ]]; then
	git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
else
	refs=`cat msquic/.git/HEAD | awk '{print $2}'`
	commit=`cat "msquic/.git/$refs"`
	if [[ "$commit" != "56cafbd0c26e1cb4e169b1233fe6d620218f6f61" ]]; then
		rm -rf ./msquic
		git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
	fi
fi

cd msquic/
git submodule update --init --recursive --recommend-shallow
