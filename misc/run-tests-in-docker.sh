#!/bin/bash

set -e

testclasses=`cat test/src/test/java/vproxy/test/VSuite.java | grep '\bTest' | cut -d '.' -f 1 | xargs`
id=`uuid`
name="vproxy-test-$id"

docker run -d -it --rm -v `pwd`:/vproxy --name "$name" wkgcass/vproxy-test:latest /bin/bash -c 'sleep 1200s'

docker exec "$name" ./gradlew clean

for cls in $testclasses
do
	echo "running $cls"
	docker exec -it "$name" ./gradlew runSingleTest -Dcase="$cls"
done

docker exec -it "$name" ./gradlew runSingleTest -Dcase="CI"

docker kill "$name"

echo "done"
