#!/bin/bash

set -e

testclasses=`cat test/src/test/java/vproxy/test/VSuite.java | grep '\bTest' | cut -d '.' -f 1 | xargs`
image="wkgcass/vproxy-test:latest"

docker pull "$image"

id=`docker run --rm "$image" uuid 2>/dev/null | cut -d '-' -f 1`
echo "random id: \`$id'"
name="vproxy-test-$id"

docker run -d --rm -v `pwd`:/vproxy --name "$name" "$image" /bin/bash -c 'sleep 1200s'

docker exec "$name" ./gradlew clean

for cls in $testclasses
do
	echo "running $cls"
	docker exec "$name" ./gradlew runSingleTest -Dcase="$cls"
done

docker exec "$name" ./gradlew runSingleTest -Dcase="CI"

docker kill "$name"

echo "done"
