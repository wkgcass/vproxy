package vswitch;

import vfd.IP;
import vfd.MacAddress;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class IPMac {
    public final IP ip;
    public final MacAddress mac;
    public final Map<String, String> annotations;

    IPMac(IP ip, MacAddress mac, Map<String, String> annotations) {
        this.ip = ip;
        this.mac = mac;
        if (annotations == null) {
            this.annotations = Collections.emptyMap();
        } else {
            this.annotations = Collections.unmodifiableMap(annotations);
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
