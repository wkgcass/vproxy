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
	if [[ "$commit" != "b1b9c3b21ddd3696476f4db858c67319b7257692" ]]; then
		rm -rf ./msquic
		git clone https://github.com/wkgcass/msquic --branch=$MSQUIC_VERSION-modified --depth=1
	fi
fi

cd msquic/
git submodule update --init --recursive --recommend-shallow
