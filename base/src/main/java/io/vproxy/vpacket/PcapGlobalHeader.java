package io.vproxy.vpacket;

// https://wiki.wireshark.org/Development/LibpcapFileFormat#file-format
public class PcapGlobalHeader {
    public final int magicNumber;
    public final int versionMajor; // guint16
    public final int versionMinor; // guint16
    public final int thisZone; /* GMT to local correction */
    public final int sigfigs; /* accuracy of timestamps */
    public final int snaplen; /* max length of captured packets, in octets */
    public final int network; /* data link type */ // https://www.tcpdump.org/linktypes.html

    public PcapGlobalHeader(int magicNumber, int versionMajor, int versionMinor, int thisZone, int sigfigs, int snaplen, int network) {
        this.magicNumber = magicNumber;
        this.versionMajor = versionMajor;
        this.versionMinor = versionMinor;
        this.thisZone = thisZone;
        this.sigfigs = sigfigs;
        this.snaplen = snaplen;
        this.network = network;
    }

    @Override
    public String toString() {
        return "PcapGlobalHeader{" +
               "magicNumber=" + Long.toString(magicNumber & 0xffffffffL, 16) +
               ", versionMajor=" + versionMajor +
               ", versionMinor=" + versionMinor +
               ", thisZone=" + thisZone +
               ", sigfigs=" + sigfigs +
               ", snaplen=" + snaplen +
               ", network=" + network +
               '}';
    }
}
