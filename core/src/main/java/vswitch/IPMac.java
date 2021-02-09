package vswitch;

import vfd.IP;
import vfd.MacAddress;
import vproxybase.util.Annotations;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class IPMac {
    public final IP ip;
    public final MacAddress mac;
    public final Annotations annotations;

    IPMac(IP ip, MacAddress mac, Annotations annotations) {
        this.ip = ip;
        this.mac = mac;
        //noinspection ReplaceNullCheck
        if (annotations == null) {
            this.annotations = new Annotations();
        } else {
            this.annotations = annotations;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IPMac ipInfo = (IPMac) o;
        return Objects.equals(ip, ipInfo.ip) &&
            Objects.equals(mac, ipInfo.mac) &&
            Objects.equals(annotations, ipInfo.annotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, mac, annotations);
    }

    @Override
    public String toString() {
        return "IPInfo{" +
            "ip=" + ip +
            ", mac=" + mac +
            ", annotations='" + annotations + '\'' +
            '}';
    }
}
