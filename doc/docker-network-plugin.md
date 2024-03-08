# docker-network-plugin

## components

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

Save the following script to `/root/run-vproxy-docker-network-plugin.sh`, and run it to launch the plugin:

```shell
#!/bin/bash

set -e

PLUGIN_IMAGE="vproxyio/docker-network-plugin:latest"

set +e
cnt=`/usr/bin/ctr image ls | grep -F "$PLUGIN_IMAGE" | wc -l`
set -e

if [[ "$cnt" -eq 0 ]]; then
	/usr/bin/ctr image pull $PLUGIN_IMAGE --skip-verify --plain-http
fi

/usr/bin/ctr image pull $PLUGIN_IMAGE # --skip-verify --plain-http
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

```systemd
[Unit]
Description=vproxy docker network plugin

[Service]
Type=simple
ExecStart=/bin/bash /root/run-vproxy-docker-network-plugin.sh
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
RequiredBy=docker.service
```
