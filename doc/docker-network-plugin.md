# docker-network-plugin

## Requirements

You will need at least kernel 5.4 (5.10 is recommended, or 6.8 to enable checksum offloading).

## How to use

Use the following commands to install and enable the plugin:

```shell
docker plugin pull vproxyio/docker-plugin
docker plugin enable vproxyio/docker-plugin
```
