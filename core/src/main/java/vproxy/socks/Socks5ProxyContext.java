package vproxy.socks;

import vproxybase.connection.Connector;
import vproxybase.util.RingBuffer;
import vproxybase.util.nio.ByteArrayChannel;

import java.util.Arrays;

public class Socks5ProxyContext {
    public static final byte GENERAL_SOCKS_SERVER_FAILURE = 0x01;
    public static final byte CONNECTION_NOT_ALLOWED_BY_RULESET = 0x02;
    public static final byte NETWORK_UNREACHABLE = 0x03;
    public static final byte HOST_UNREACHABLE = 0x04;
    public static final byte CONNECTION_REFUSED = 0x05;
    public static final byte TTL_EXPIRED = 0x06;
    public static final byte COMMAND_NOT_SUPPORTED = 0x07;
    public static final byte ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    // state:
    // 0: expecting version
    // 1: expecting auth method count
    // 2: expecting methods
    // 3: (write data to client)
    // 4: expecting version
    // 5: expecting command
    // 6: expecting preserved
    // 7: expecting req_type
    // 8: expecting address
    // 9: expecting port
    //10: (find server and write data to client)
    //11: (make callback)
    //12: (done)
    // other: error
    int state;
    byte errType;
    boolean isDoingAuth = true;
    boolean done = false;

    int clientMethodLeft; // first set the AUTH METHOD COUNT, then self decrease until 0
    byte[] clientSupportedMethods;
    byte clientCommand; // 0x01: CONNECT, 0x02: BIND, 0x03: UDP ASSOC, we only support CONNECT for now
    AddressType reqType; // 0x01: ipv4, 0x03: domain, 0x04: ipv6
    int addressLeft; // first set to 4(ipv4)or16(ipv6)or user specific(domain), then self decrease until 0
    byte[] address;
    int portLeft = 2;
    byte[] portBytes = new byte[2];
    int port;
    Connector connector;

    final RingBuffer inBuffer;
    private final byte[] b = new byte[1];
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(b);

    public Socks5ProxyContext(RingBuffer inBuffer) {
        this.inBuffer = inBuffer;
    }

    boolean hasNext() {
        return inBuffer.used() > 0;
    }

    byte next() {
        chnl.reset();
        inBuffer.writeTo(chnl);
        return b[0];
    }

    @Override
    public String toString() {
        return "Socks5ProxyContext{" +
            "state=" + state +
            ", errType=" + errType +
            ", isDoingAuth=" + isDoingAuth +
            ", done=" + done +
            ", clientMethodLeft=" + clientMethodLeft +
            ", clientSupportedMethods=" + Arrays.toString(clientSupportedMethods) +
            ", clientCommand=" + clientCommand +
            ", reqType=" + reqType +
            ", addressLeft=" + addressLeft +
            ", address=" + Arrays.toString(address) +
            ", portLeft=" + portLeft +
            ", portBytes=" + Arrays.toString(portBytes) +
            ", port=" + port +
            ", connector=" + connector +
            ", b=" + Arrays.toString(b) +
            '}';
    }
}

/*
 * client -> server
 * +-------------+-----------------------+-----------------+
 * | VERSION (1) | AUTH METHOD COUNT (1) | METHODS (1~255) |
 * +-------------+-----------------------+-----------------+
 * 0x00: no auth
 * 0x01: gssapi
 * 0x02: user/pass
 *
 * server -> client
 * +-------------+------------+
 * | VERSION (1) | METHOD (1) |
 * +-------------+------------+
 * 0x00: no auth
 * 0x01: further auth
 * 0xFF: no method
 *
 * client -> server
 * +-------------+-------------+---------------+--------------+-------------+----------+
 * | VERSION (1) | COMMAND (1) | preserved (1) | REQ_TYPE (1) | ADDRESS (n) | PORT (2) |
 * +-------------+-------------+---------------+--------------+-------------+----------+
 * 0x01: CONNECT
 * 0x02: BIND
 * 0x03: UDP ASSOC
 * --
 * 0x01: ipv4
 * 0x03: domain
 * 0x04: ipv6
 *
 * server -> client
 * +-------------+----------+---------------+---------------+-------------+----------+
 * | VERSION (1) | RESP (1) | preserved (1) | RESP_TYPE (1) | ADDRESS (n) | PORT (2) |
 * +-------------+----------+---------------+---------------+-------------+----------+
 * | 5           |          | 0             | 0             | 0           | 0        |
 * +-------------+----------+---------------+---------------+-------------+----------+
 * 0x00: succeeded
 * 0x01: general SOCKS server failure
 * 0x02: connection not allowed by ruleset
 * 0x03: Network unreachable
 * 0x04: Host unreachable
 * 0x05: Connection refused
 * 0x06: TTL expired
 * 0x07: Command not supported
 * 0x08: Address type not supported
 * the response type, address, port fields are for BIND, and we don not support for now
 */
