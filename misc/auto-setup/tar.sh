#/bin/bash

set -o xtrace
set -e

NAME="$1"
FILE="$2"
filename=`basename "$FILE"`
cd `dirname $FILE`
tar zcf "$NAME" "$filename"
