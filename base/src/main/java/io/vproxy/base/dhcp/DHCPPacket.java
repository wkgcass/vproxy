package io.vproxy.base.dhcp;

import io.vproxy.base.dhcp.options.*;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

// https://tools.ietf.org/html/rfc2131
public class DHCPPacket {
    private static final Map<Byte, Supplier<DHCPOption>> type2optionMap = new HashMap<>();

    static {
        type2optionMap.put(Consts.DHCP_OPT_TYPE_END, EndOption::new);
        type2optionMap.put(Consts.DHCP_OPT_TYPE_PAD, PadOption::new);
        type2optionMap.put(Consts.DHCP_OPT_TYPE_MSG_TYPE, MessageTypeOption::new);
        type2optionMap.put(Consts.DHCP_OPT_TYPE_PARAM_REQ_LIST, ParameterRequestListOption::new);
        type2optionMap.put(Consts.DHCP_OPT_TYPE_DNS, DNSOption::new);
    }

    /* 1 */public byte op; // Message op code / message type. 1 = BOOTREQUEST, 2 = BOOTREPLY
    /* 1 */public byte htype = Consts.DHCP_HTYPE_ETHERNET; // Hardware address type, 1 = ethernet
    /* 1 */public byte hlen = 6; // Hardware address length
    /* 1 */public byte hops = 0; // Client sets to zero, optionally used by relay agents when booting via a relay agent.
    /* 4 */public int xid = ThreadLocalRandom.current().nextInt();
    //                        Transaction ID, a random number chosen by the
    //                        client, used by the client and server to associate
    //                        messages and responses between a client and a
    //                        server.
    /* 2 */public int secs = 0; // Filled in by client, seconds elapsed since client
    //                             began address acquisition or renewal process.
    /* 2 */public int flags = 0;
    /* 4 */public IP ciaddr = IP.from("0.0.0.0");
    //                          Client IP address; only filled in if client is in
    //                          BOUND, RENEW or REBINDING state and can respond
    //                          to ARP requests.
    /* 4 */public IP yiaddr = IP.from("0.0.0.0"); // 'your' (client) IP address.
    /* 4 */public IP siaddr = IP.from("0.0.0.0");
    //                          IP address of next server to use in bootstrap;
    //                          returned in DHCPOFFER, DHCPACK by server.
    /* 4 */public IP giaddr = IP.from("0.0.0.0");
    //                          Relay agent IP address, used in booting via a
    //                          relay agent.
    /* 16 */public MacAddress chaddr; // Client hardware address.
    /* 64 */public ByteArray sname = null; // Optional server host name, null terminated string.
    /* 128 */public ByteArray file = null; // Boot file name, null terminated string; "generic"
    //                                     name or null in DHCPDISCOVER, fully qualified
    //                                     directory-path name in DHCPOFFER.
    /* 4 */public int cookie = Consts.DHCP_MAGIC_COOKIE; // magic cookie
    /* var */public LinkedList<DHCPOption> options = new LinkedList<>(); // Optional parameters field

    public ByteArray serialize() {
        ByteArray ret = ByteArray.allocate(12)
            .set(0, op).set(1, htype).set(2, hlen).set(3, hops)
            .int32(4, xid).int16(8, secs).int16(10, flags)
            .concat(ByteArray.from(ciaddr.getAddress()))
            .concat(ByteArray.from(yiaddr.getAddress()))
            .concat(ByteArray.from(siaddr.getAddress()))
            .concat(ByteArray.from(giaddr.getAddress()))
            .concat(chaddr.bytes)
            .concat(ByteArray.allocate((16 - 6)));
        if (sname == null) {
            ret = ret.concat(ByteArray.allocate(64));
        } else {
            ret = ret.concat(sname);
            if (sname.length() < 64) {
                ByteArray pad = ByteArray.from(64 - sname.length());
                ret = ret.concat(pad);
            }
        }
        if (file == null) {
            ret = ret.concat(ByteArray.allocate(128));
        } else {
            ret = ret.concat(file);
            if (file.length() < 128) {
                ByteArray pad = ByteArray.from(128 - file.length());
                ret = ret.concat(pad);
            }
        }
        var cookieArr = ByteArray.allocate(4);
        cookieArr.int32(0, cookie);
        ret = ret.concat(cookieArr);
        if (options.getLast().type != (byte) 0xff) {
            options.add(new EndOption());
        }
        for (DHCPOption opt : options) {
            ret = ret.concat(opt.serialize());
        }

        return ret;
    }

    public void deserialize(ByteArray arr) throws Exception {
        if (arr.length() < (28 + 16 + 64 + 128 + 4)) {
            throw new Exception("failed parsing dhcp packet, too short, length: " + arr.length());
        }
        op = arr.get(0);
        htype = arr.get(1);
        if (htype != 1) {
            throw new Exception("unknown htype: not 1(ethernet): " + htype);
        }
        hlen = arr.get(2);
        if (hlen != 6) {
            throw new Exception("unexpected hlen: not 6: " + hlen);
        }
        hops = arr.get(3);
        xid = arr.int32(4);
        secs = arr.uint16(8);
        flags = arr.uint16(10);
        ciaddr = IP.from(arr.sub(12, 4).toJavaArray());
        yiaddr = IP.from(arr.sub(16, 4).toJavaArray());
        siaddr = IP.from(arr.sub(20, 4).toJavaArray());
        giaddr = IP.from(arr.sub(24, 4).toJavaArray());
        chaddr = new MacAddress(arr.sub(28, 6));
        sname = arr.sub(44, 64);
        file = arr.sub(108, 128);
        cookie = arr.int32(236);

        ByteArray optionsArr = arr.sub(240, arr.length() - 240);
        while (true) {
            if (optionsArr.length() == 0) {
                break;
            }
            byte type = optionsArr.get(0);
            var supplier = type2optionMap.get(type);
            DHCPOption dhcpOption;
            if (supplier == null) {
                dhcpOption = new DHCPOption();
            } else {
                dhcpOption = supplier.get();
            }
            int lenRead = dhcpOption.deserialize(optionsArr);
            optionsArr = optionsArr.sub(lenRead, optionsArr.length() - lenRead);
            options.add(dhcpOption);
            if (dhcpOption instanceof EndOption) {
                break;
            }
        }
    }

    @Override
    public String toString() {
        return "DHCPPacket{" +
            "op=" + op +
            ", htype=" + htype +
            ", hlen=" + hlen +
            ", hops=" + hops +
            ", xid=" + xid +
            ", secs=" + secs +
            ", flags=" + flags +
            ", ciaddr=" + ciaddr +
            ", yiaddr=" + yiaddr +
            ", siaddr=" + siaddr +
            ", giaddr=" + giaddr +
            ", chaddr=" + chaddr +
            ", sname='" + sname + '\'' +
            ", file='" + file + '\'' +
            ", options=" + options +
            '}';
    }
}
