# docker-network-plugin

## Requirements

You will need at least kernel 5.4 (5.10 is recommended, or 6.8 to enable checksum offloading).

## How to use

### vproxyio/docker-plugin

`vproxyio/docker-plugin` is simply a proxy:

It binds to `vproxy.sock` (in plugin dir) and proxies all traffic to&from `/var/run/docker/vproxy_network_plugin.sock`.

Use the following commands to install and enable the plugin:

```shell
docker plugin pull vproxyio/docker-plugin
docker plugin enable vproxyio/docker-plugin
```

> Note: normally you will need to launch `vproxyio/docker-network-plugin` first.

### vproxyio/docker-network-plugin

`vproxyio/docker-network-plugin` is the actual plugin.

Save the following script to `/usr/local/bin/run-vproxy-docker-network-plugin.sh`, and run it to launch the plugin:

```shell
#!/bin/bash

set -e

PLUGIN_IMAGE="docker.io/vproxyio/docker-network-plugin:latest"

set +e
cnt=`/usr/bin/ctr image ls | grep -F "$PLUGIN_IMAGE" | wc -l`
set -e

if [[ "$cnt" -eq 0 ]]; then
	/usr/bin/ctr image pull $PLUGIN_IMAGE --skip-verify --plain-http
fi

set +e
ctr snapshot rm vproxy-docker-network-plugin
ctr container rm vproxy-docker-network-plugin
set -e

exec /usr/bin/ctr run \
	--rm --net-host \
	--mount type=bind,src=/etc,dst=/x-etc,options=rbind \
	--mount type=bind,src=/var/run/docker,dst=/var/run/docker,options=rbind \
	--mount type=bind,src=/dev/net,dst=/dev/net,options=rbind:ro \
	--mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,options=rbind:ro \
	--cap-add CAP_NET_ADMIN \
	--cap-add CAP_SYS_RESOURCE \
	--cap-add CAP_SYS_ADMIN \
	--device=/dev/net/tun \
	$PLUGIN_IMAGE \
	vproxy-docker-network-plugin
```

This must be running before the docker daemon initiates, so you may consider using the following systemd config:

`/etc/systemd/system/vproxy-docker-network-plugin.service`

```systemd
[Unit]
Description=vproxy docker network plugin

[Service]
Type=simple
ExecStart=/bin/bash /usr/local/bin/run-vproxy-docker-network-plugin.sh
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
RequiredBy=docker.service
```

Use `journalctl -u vproxy-docker-network-plugin -f -o cat` to view the logs of the container.
