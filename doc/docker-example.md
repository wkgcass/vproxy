# Docker

VProxy provides a simple Dockerfile, you can use it to make an images and run.

## Build

```
docker build --no-cache -t vproxy:latest https://raw.githubusercontent.com/wkgcass/vproxy/master/docker/Dockerfile
```

Use the `--no-cache` flag because the Dockerfile dynamically retrieves the latest vproxy jar with the same RUN statements.

## Run

```
docker run -d -v $AUTO_SAVE_DIR:/root vproxy:latest
```

The `$AUTO_SAVE_DIR` is a directory for you to load and store automatically saved file (named `.vproxy.last`).  

If you have some ports, you may need to expose them using `-p` option. Or simply use the `--net=host` option if it's possible.

## Check

```
docker logs $CONTAINER_ID -f
```

## Quit

Use SIGTERM to save current config and quit.

```
docker stop $CONTAINER_ID
```

## Customized launch parameters

The dockerfile uses entrypoint, so any param can be simply added when `run`ning the container. You may directly specify system properties using `-Dxxx=yyy` just as using normal application params, the vproxy `main` function will convert them to system properties.
