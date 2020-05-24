package vpacket;

import vfd.IP;
import vproxybase.util.ByteArray;
import vproxybase.util.Consts;
import vproxybase.util.Utils;

import java.util.Objects;

public class ArpPacket extends AbstractPacket {
    private int hardwareType;
    private int protocolType;
    private int hardwareSize;
    private int protocolSize;
    private int opcode;
    private ByteArray senderMac;
    private ByteArray senderIp;
    private ByteArray targetMac;
    private ByteArray targetIp;

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 2) {
            return "input packet length too short for an arp packet: no hardwareType found";
        }
        hardwareType = bytes.uint16(0);
        if (bytes.length() < 4) {
            return "input packet length too short for an arp packet: no protocolType found";
        }
        protocolType = bytes.uint16(2);
        if (bytes.length() < 5) {
            return "input packet length too short for an arp packet: no hardwareSize found";
        }
        hardwareSize = bytes.uint8(4);
        if (bytes.length() < 6) {
            return "input packet length too short for an arp packet: no protocolSize found";
        }
        protocolSize = bytes.uint8(5);
        if (bytes.length() < 8) {
            return "input packet length too short for an arp packet: no opcode found";
        }
        opcode = bytes.uint16(6);
        if (bytes.length() < 8 + hardwareSize) {
            return "input packet length too short for an arp packet: no enough bytes for senderMac";
        }
        senderMac = bytes.sub(8, hardwareSize);
        if (bytes.length() < 8 + hardwareSize + protocolSize) {
            return "input packet length too short for an arp packet: no enough bytes for senderIp";
        }
        senderIp = bytes.sub(8 + hardwareSize, protocolSize);
        if (bytes.length() < 8 + hardwareSize + protocolSize + hardwareSize) {
            return "input packet length too short for an arp packet: no enough bytes for targetMac";
        }
        targetMac = bytes.sub(8 + hardwareSize + protocolSize, hardwareSize);
        if (bytes.length() < 8 + hardwareSize + protocolSize + hardwareSize + protocolSize) {
            return "input packet length too short for an arp packet: no enough bytes for targetIp";
        }
        targetIp = bytes.sub(8 + hardwareSize + protocolSize + hardwareSize, protocolSize);
        if (bytes.length() != 8 + 2 * hardwareSize + 2 * protocolSize) {
            return "input packet has extra bytes for an arp packet: " + (bytes.length() - (8 + 2 * hardwareSize + 2 * protocolSize) + " bytes");
        }

        raw = bytes;
        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        // pre-check
        if (senderMac.length() != targetMac.length())
            throw new IllegalArgumentException("sender mac and target mac length not the same");
        if (senderIp.length() != targetIp.length())
            throw new IllegalArgumentException("sender ip and target ip length not the same");

        // fill
        hardwareSize = senderMac.length();
        protocolSize = senderIp.length();

        // generate
        return ByteArray.allocate(8)
            .int16(0, hardwareType)
            .int16(2, protocolType)
            .set(4, (byte) hardwareSize).set(5, (byte) protocolSize)
            .int16(6, opcode)
            .concat(senderMac)
            .concat(senderIp)
            .concat(targetMac)
            .concat(targetIp);
    }

    @Override
    public String toString() {
        if (protocolType == Consts.ARP_PROTOCOL_TYPE_IP) {
            if (opcode == Consts.ARP_PROTOCOL_OPCODE_REQ) { // request
                if (targetIp != null && targetIp.length() == 4 && senderIp != null && senderIp.length() == 4) {
                    return "ArpPacket(" +
                        "who has " + IP.ipStr(targetIp.toJavaArray()) + "?" +
                        " tell " + IP.ipStr(senderIp.toJavaArray())
                        + " senderMac=" + Utils.runAvoidNull(() -> senderMac.toHexString(), "null")
                        + " targetMac=" + Utils.runAvoidNull(() -> targetMac.toHexString(), "null")
                        + ")";
                }
            } else if (opcode == Consts.ARP_PROTOCOL_OPCODE_RESP) { // response
                if (senderIp != null && senderIp.length() == 4 && senderMac != null) {
                    return "ArpPacket(" +
                        IP.ipStr(senderIp.toJavaArray()) + " is at " + senderMac.toHexString()
                        + " targetMac=" + Utils.runAvoidNull(() -> targetMac.toHexString(), "null")
                        + " targetIp=" + Utils.runAvoidNull(() -> targetIp.toHexString(), "null")
                        + ")";
                }
            }
        }
        return "ArpPacket{" +
            "hardwareType=" + Utils.toHexString(hardwareType) +
            ", protocolType=" + Utils.toHexString(protocolType) +
            ", hardwareSize=" + hardwareSize +
            ", protocolSize=" + protocolSize +
            ", opcode=" + Utils.toHexString(opcode) +
            ", senderMac=" + Utils.runAvoidNull(() -> senderMac.toHexString(), "null") +
            ", senderIp=" + Utils.runAvoidNull(() -> senderIp, "null") +
            ", targetMac=" + Utils.runAvoidNull(() -> targetMac, "null") +
            ", targetIp=" + Utils.runAvoidNull(() -> targetIp, "null") +
            '}';
    }

    public int getHardwareType() {
        return hardwareType;
    }

    public void setHardwareType(int hardwareType) {
        clearRawPacket();
        this.hardwareType = hardwareType;
    }

    public int getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(int protocolType) {
        clearRawPacket();
        this.protocolType = protocolType;
    }

    public int getHardwareSize() {
        return hardwareSize;
    }

    public void setHardwareSize(int hardwareSize) {
        clearRawPacket();
        this.hardwareSize = hardwareSize;
    }

    public int getProtocolSize() {
        return protocolSize;
    }

    public void setProtocolSize(int protocolSize) {
        clearRawPacket();
        this.protocolSize = protocolSize;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        clearRawPacket();
        this.opcode = opcode;
    }

    public ByteArray getSenderMac() {
        return senderMac;
    }

    public void setSenderMac(ByteArray senderMac) {
        clearRawPacket();
        this.senderMac = senderMac;
    }

    public ByteArray getSenderIp() {
        return senderIp;
    }

    public void setSenderIp(ByteArray senderIp) {
        clearRawPacket();
        this.senderIp = senderIp;
    }

    public ByteArray getTargetMac() {
        return targetMac;
    }

    public void setTargetMac(ByteArray targetMac) {
        clearRawPacket();
        this.targetMac = targetMac;
    }

    public ByteArray getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(ByteArray targetIp) {
        clearRawPacket();
        this.targetIp = targetIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArpPacket arpPacket = (ArpPacket) o;
        return hardwareType == arpPacket.hardwareType &&
            protocolType == arpPacket.protocolType &&
            hardwareSize == arpPacket.hardwareSize &&
            protocolSize == arpPacket.protocolSize &&
            opcode == arpPacket.opcode &&
            Objects.equals(senderMac, arpPacket.senderMac) &&
            Objects.equals(senderIp, arpPacket.senderIp) &&
            Objects.equals(targetMac, arpPacket.targetMac) &&
            Objects.equals(targetIp, arpPacket.targetIp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hardwareType, protocolType, hardwareSize, protocolSize, opcode, senderMac, senderIp, targetMac, targetIp);
    }
}
