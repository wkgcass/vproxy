#!/bin/bash

set +e
./gradlew --stop

set -e

testclasses=`cat test/src/test/java/io/vproxy/test/VSuite.java | grep '\bTest' | cut -d '.' -f 1 | xargs`
image="vproxyio/test:latest"

docker pull "$image"

id=`docker run --rm "$image" uuid 2>/dev/null | cut -d '-' -f 1`
echo "random id: \`$id'"
name="vproxy-test-$id"

docker run -d --privileged --net=host -v `pwd`:/vproxy-2 --name "$name" "$image" /bin/bash -c 'sleep 300s'

docker exec "$name" /bin/bash -c 'cp -r /vproxy-2/* /vproxy/'

docker exec "$name" make clean vfdposix

for cls in $testclasses
do
	echo "running $cls"
	docker kill "$name"
	docker start "$name"
	set +e
	docker exec "$name" ./gradlew runSingleTest -Dcase="$cls" --no-daemon
	exit_code="$?"
	if [ "$exit_code" != "0" ]
	then
		docker kill "$name"
		docker rm "$name"
		exit $exit_code
	fi
	set -e
done

docker kill "$name"
docker start "$name"
set +e
docker exec "$name" ./gradlew runSingleTest -Dcase="CI" --no-daemon
exit_code="$?"
if [ "$exit_code" != "0" ]
then
	docker kill "$name"
	docker rm "$name"
	exit $exit_code
fi
set -e

docker kill "$name"
docker start "$name"
set +e
docker exec "$name" ./gradlew runPanamaTest --no-daemon
exit_code="$?"
if [ "$exit_code" != "0" ]
then
	docker kill "$name"
	docker rm "$name"
	exit "$exit_code"
fi
set -e

docker kill "$name"
docker rm "$name"

echo "done"
