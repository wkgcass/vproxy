package vproxy.base.util.net;

import vproxy.vfd.MacAddress;

public class Nic {
    public final String name;
    public final MacAddress mac;
    public final int speed;
    public final boolean isVirtual;

    public Nic(String name, MacAddress mac, int speed, boolean isVirtual) {
        this.name = name;
        this.mac = mac;
        this.speed = speed;
        this.isVirtual = isVirtual;
    }
}
