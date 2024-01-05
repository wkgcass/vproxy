package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;

// https://datatracker.ietf.org/doc/draft-ietf-opsawg-pcap/
// https://wiki.wireshark.org/Development/LibpcapFileFormat#file-format
public class PcapGlobalHeader {
    public final int magicNumber;
    public final int versionMajor; // guint16
    public final int versionMinor; // guint16
    public final int reserved1; /* from rfc: reserved, from wiki: GMT to local correction */
    public final int reserved2; /* from rfc: reserved, from wiki: accuracy of timestamps */
    public final int snaplen; /* max length of captured packets, in octets */
    public final int dataLinkType; // https://www.tcpdump.org/linktypes.html

    public PcapGlobalHeader() {
        this(4096, LINKTYPE_ETHERNET);
    }

    public PcapGlobalHeader(int snaplen, int dataLinkType) {
        this(0xA1B2C3D4, 2, 4, 0, 0, snaplen, dataLinkType);
    }

    public PcapGlobalHeader(int magicNumber, int versionMajor, int versionMinor, int reserved1, int reserved2, int snaplen, int dataLinkType) {
        this.magicNumber = magicNumber;
        this.versionMajor = versionMajor;
        this.versionMinor = versionMinor;
        this.reserved1 = reserved1;
        this.reserved2 = reserved2;
        this.snaplen = snaplen;
        this.dataLinkType = dataLinkType;
    }

    public static final int LINKTYPE_NULL = 0;
    public static final int LINKTYPE_ETHERNET = 1;
    public static final int LINKTYPE_LINUX_SLL = 113;

    public ByteArray build() {
        return ByteArray.allocate(24)
            .int32ReverseNetworkByteOrder(0, magicNumber)
            .int16ReverseNetworkByteOrder(4, versionMajor)
            .int16ReverseNetworkByteOrder(6, versionMinor)
            .int32ReverseNetworkByteOrder(8, reserved1)
            .int32ReverseNetworkByteOrder(12, reserved2)
            .int32ReverseNetworkByteOrder(16, snaplen)
            .int32ReverseNetworkByteOrder(20, dataLinkType);
    }

    @Override
    public String toString() {
        return "PcapGlobalHeader{" +
               "magicNumber=" + Long.toString(magicNumber & 0xffffffffL, 16) +
               ", versionMajor=" + versionMajor +
               ", versionMinor=" + versionMinor +
               ", reserved1(thisZone)=" + reserved1 +
               ", reserved2(sigfigs)=" + reserved2 +
               ", snaplen=" + snaplen +
               ", dataLinkType=" + dataLinkType +
               '}';
    }
}
