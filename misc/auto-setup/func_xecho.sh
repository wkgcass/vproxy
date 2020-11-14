#!/bin/bash

export XECHO="1"

function xecho() {
	x=`echo "$@"`
	printf "\033[0;32m%s\033[0m\n" "$x"
}
