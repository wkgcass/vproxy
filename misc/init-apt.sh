#!/bin/bash

set -e

echo "You might want to run:"
echo "    git config --global core.autocrlf false"
echo "---"

echo "installing necessary softwares"
sudo apt install -y git build-essential zip libelf-dev libbpf-dev cmake
echo "---"

echo "'make init' to initiate this project"
make init

echo "'make clean' to ensure the build system works as expected"
make clean
echo "---"

echo "'make fubuki' to download fubuki required libraries"
make fubuki
echo "---"

echo "'make jar-with-lib' to build the project"
make jar-with-lib
