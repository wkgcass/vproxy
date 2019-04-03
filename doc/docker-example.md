# Docker

VProxy provides a simple Dockerfile, you can use it to make an images and run.

## Build

```
docker build --no-cache -t vproxy:latest https://raw.githubusercontent.com/wkgcass/vproxy/master/vproxy/Dockerfile
```

Use the `--no-cache` flag because the Dockerfile dynamically retrieves the latest vproxy jar with the same RUN statements.

## Run

```
docker run -d -v $AUTO_SAVE_DIR:/root -p 16379:16379 vproxy:latest
```

The `$AUTO_SAVE_DIR` is a directory for you to load and store automatically saved file (named `.vproxy.last`).  
The `16379` is the resp-controller port defined in Dockerfile, expose it then you can manage the vproxy instance using `redis-cli`.

If you have more ports, expose them as well. Or simply use the `--net=host` option if it's possible.

## Check

```
docker logs $CONTAINER_ID -f
```

## Quit

Use SIGHUP to save current config and quit.

```
docker kill --signal=HUP $CONTAINER_ID
```

## Customized launch parameters

* Use `java` for the java program.
* Use `/vproxy.jar` or `vproxy.jar` for the vproxy jar.
* And remember to escape `"~"` if you are trying to use the home directory inside the container.

e.g.

```
docker run -p 1080:1080 --read-only -v /path-to-config-dir:/root vproxy:latest java -Deploy=WebSocksProxyAgent -jar vproxy.jar "~/vproxy-websocks-agent.conf"
```
