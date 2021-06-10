## misc

* `apidoc/`: Some scripts to generate common objects in api.yaml
* `auto-setup/`: A tool chain utilizing virtualbox to construct an environment to build and test all components of vproxy, including vswitch, docker-plugin, k8s controller etc...
* `ca/`: Scripts and configurations to help you generate self-signed certificates
* `gen-exports.py`: Generate all `exports` statements in `module-info.java`
* `graal-jni.json`: JNI config used by graal to generate native image
* `graal-reflect.json`: Reflect config used by graal to generate native image
* `mirror-switch.json`: An example about the `mirror` function provided by vproxy
* `netnsutils.py`: A script which helps you generate and configure virtual ports in vproxy vswitch
* `run-tests-in-docker.sh`: A script which helps you run tests in docker container (requires docker ipv6)
* `switch-test-init.sh`: A script which automatically builds a network described [here](https://github.com/wkgcass/vproxy/blob/dev/doc/switch.md#example-topology), and uses ping to test the network
* `vp-ws-server-native.service`: The systemd service file for launching a graal native image version of vproxy websocks proxy server
* `vp-ws-server.service`: The systemd service file for launching a normal vproxy websocks proxy server
* `vproxy.service`: The vproxy instance systemd service file template
