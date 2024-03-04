# Integrate virtual switch and fubuki

## Abstraction

`vproxy` provides a sound virtual switch implementation, which supports the TCP/IP stack;
`fubuki` is a network mesh based on tun interfaces written in rust.

We can combine them by converting fubuki tun interfaces into rx/tx APIs and hooking them into vproxy virtual switch.

## Commands descriptions

After launching vproxy, we can get detailed into about the commands used in this article:

* `man switch`
* `man vpc`
* `man fubuki`
* `man iface`
* `man switch add`
* `man vpc add-to`
* `man fubuki add-to`
* `man iface list-detail`
* `man iface remove-from`

## Configuring

1. Launching vproxy

```
make jar-with-lib
java --enable-preview -Dvfd=posix -jar build/libs/vproxy.jar
```

2. Creating virtual switch

```
add switch sw0
```

3. Creating virtual network

```
add vpc 1 to switch sw0 v4network 10.99.88.0/24
```

Descriptions:

* `add vpc 1` means creating vpc `1`, whose vni is 1
* `to switch sw0` means it's added into `sw0`
* `v4network $.$.$.$/$` means the v4 network range limit of this vpc

4. Creating fubuki interface

```
add fubuki fbk0 to switch sw0 vni 1 mac 00:11:22:33:44:55 ip 10.99.88.199/24 address $.$.$.$:$ password $
```

Descriptions:

* `add fubuki fbk0` means create a fubuki interface, named as `fbk0`
* `to switch sw0` means it's added into `sw0`
* `vni 1` means the interface by default belongs to the vpc whose vni is 1 (in other words, `vpc 1`)
* `mac $:$:$:$:$:$` means the mac address allocated for the interface. Since fubuki runs in tun mode, the switch has to simulate the layer 2 frames
* `ip $.$.$.$/$` means the ip address and mask for this interface to use. You may omit this option, in this case, fubuki will automatically allocate an ip instead
* `address $.$.$.$:$` means the address and port of the remote fubuki server
* `password $` means the password used by fubuki to communicate

## Viewing

```
ll iface in switch sw0
```

## Testing

Use a standard fubuki client and connect to the same server, then ping the ip address bond by `fbk0`

```
PING 10.99.88.199 (10.99.88.199): 56 data bytes
Request timeout for icmp_seq 0
64 bytes from 10.99.88.199: icmp_seq=1 ttl=64 time=92.467 ms
64 bytes from 10.99.88.199: icmp_seq=2 ttl=64 time=90.040 ms
64 bytes from 10.99.88.199: icmp_seq=3 ttl=64 time=92.859 ms
^C
--- 10.99.88.199 ping statistics ---
4 packets transmitted, 3 packets received, 25.0% packet loss
round-trip min/avg/max/stddev = 90.040/91.789/92.859/1.247 ms
```

Note: vproxy virtual switch must lookup the dst mac address, so the first responding packet cannot be transmitted.
All other packets after the first one should be transmitted normally.

> For tun devices, arp/ns will be transformed into special icmp packets.

## Deleting

```
remove iface fubuki:fbk0 from switch sw0
```
