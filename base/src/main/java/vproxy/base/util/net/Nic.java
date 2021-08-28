package vproxy.base.util.net;

import vproxy.vfd.MacAddress;

public class Nic {
    public final String name;
    public final MacAddress mac;

    public Nic(String name, MacAddress mac) {
        this.name = name;
        this.mac = mac;
    }
}
