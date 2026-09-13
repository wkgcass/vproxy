package io.vproxy.vpacket.icmpv6;

import io.vproxy.base.util.ByteArray;

/**
 * ICMPv6 Router Advertisement message body (RFC 4861 4.2),
 * i.e. the icmp packet content after the 4-byte icmp header.
 * Layout: curHopLimit(1) + flags(1) + routerLifetime(2) + reachableTime(4) + retransTimer(4) + options.
 */
public class RouterAdvertisementPacket extends AbstractNdpPacket {
    public static final int FLAG_MANAGED_ADDRESS_CONFIG = 0x80; // M
    public static final int FLAG_OTHER_CONFIG = 0x40; // O

    private int currentHopLimit;
    private int flags;
    private int routerLifetime; // in seconds, 0 means this router is not a default router
    private long reachableTime; // in milliseconds, 0 means unspecified
    private long retransmissionTimer; // in milliseconds, 0 means unspecified

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 12) { // the fixed-size ra header
            return "input length too short for a router advertisement message";
        }
        currentHopLimit = bytes.uint8(0);
        flags = bytes.uint8(1);
        routerLifetime = bytes.uint16(2);
        reachableTime = bytes.uint32(4);
        retransmissionTimer = bytes.uint32(8);
        parseOptions(bytes, 12);
        return null;
    }

    public boolean isManagedAddressConfig() {
        return (flags & FLAG_MANAGED_ADDRESS_CONFIG) != 0;
    }

    public void setManagedAddressConfig(boolean managed) {
        flags = managed ? flags | FLAG_MANAGED_ADDRESS_CONFIG : flags & ~FLAG_MANAGED_ADDRESS_CONFIG;
    }

    public boolean isOtherConfig() {
        return (flags & FLAG_OTHER_CONFIG) != 0;
    }

    public void setOtherConfig(boolean otherConfig) {
        flags = otherConfig ? flags | FLAG_OTHER_CONFIG : flags & ~FLAG_OTHER_CONFIG;
    }

    /**
     * Default router preference, 2 bits (RFC 4191): 1=high, 0=medium, 3=low.
     */
    public int getDefaultRouterPreference() {
        return (flags >> 3) & 0x3;
    }

    public void setDefaultRouterPreference(int prf) {
        flags = (flags & ~0x18) | ((prf & 0x3) << 3);
    }

    public ByteArray toByteArray() {
        return ByteArray.allocate(12)
            .set(0, (byte) currentHopLimit)
            .set(1, (byte) flags)
            .int16(2, routerLifetime)
            .int32(4, (int) reachableTime)
            .int32(8, (int) retransmissionTimer)
            .concat(optionsToByteArray());
    }

    @Override
    public String toString() {
        return "RouterAdvertisementPacket{" +
            "currentHopLimit=" + currentHopLimit +
            ", managedAddressConfig=" + isManagedAddressConfig() +
            ", otherConfig=" + isOtherConfig() +
            ", defaultRouterPreference=" + getDefaultRouterPreference() +
            ", routerLifetime=" + routerLifetime +
            ", reachableTime=" + reachableTime +
            ", retransmissionTimer=" + retransmissionTimer +
            ", options=" + getOptions() +
            '}';
    }

    public int getCurrentHopLimit() {
        return currentHopLimit;
    }

    public void setCurrentHopLimit(int currentHopLimit) {
        this.currentHopLimit = currentHopLimit;
    }

    public int getRouterLifetime() {
        return routerLifetime;
    }

    public void setRouterLifetime(int routerLifetime) {
        this.routerLifetime = routerLifetime;
    }

    public long getReachableTime() {
        return reachableTime;
    }

    public void setReachableTime(long reachableTime) {
        this.reachableTime = reachableTime;
    }

    public long getRetransmissionTimer() {
        return retransmissionTimer;
    }

    public void setRetransmissionTimer(long retransmissionTimer) {
        this.retransmissionTimer = retransmissionTimer;
    }
}
