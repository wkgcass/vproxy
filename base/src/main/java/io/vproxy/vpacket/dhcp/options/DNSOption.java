package io.vproxy.vpacket.dhcp.options;

import io.vproxy.vpacket.dhcp.DHCPOption;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IP;

import java.util.LinkedList;
import java.util.List;

public class DNSOption extends DHCPOption {
    public final List<IP> dnsList;

    public DNSOption() {
        this(null);
    }

    public DNSOption(List<IP> dnsList) {
        this.type = Consts.DHCP_OPT_TYPE_DNS;
        this.dnsList = dnsList == null ? new LinkedList<>() : new LinkedList<>(dnsList);
    }

    public DNSOption add(IP ip) {
        dnsList.add(ip);
        return this;
    }

    @Override
    public ByteArray serialize() {
        this.len = this.dnsList.size() * 4;
        this.content = ByteArray.allocate(0);
        for (IP ip : dnsList) {
            content = content.concat(ByteArray.from(ip.getAddress()));
        }
        return super.serialize();
    }

    @Override
    public int deserialize(ByteArray arr) throws Exception {
        int n = super.deserialize(arr);
        if ((n - 2) % 4 != 0) {
            throw new Exception("invalid dhcp dns option, len%4 != 0");
        }
        for (int i = 0; i < content.length(); i += 4) {
            byte[] ipBytes = content.sub(i, 4).toJavaArray();
            IP ip = IP.from(ipBytes);
            dnsList.add(ip);
        }
        return n;
    }

    @Override
    public String toString() {
        return "DNSOption{" +
            "ips=" + dnsList +
            '}';
    }
}
