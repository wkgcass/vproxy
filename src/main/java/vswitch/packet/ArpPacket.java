package vswitch.packet;

import vproxy.util.ByteArray;
import vproxy.util.Utils;
import vswitch.util.Consts;

import static vproxy.util.Utils.runAvoidNull;

public class ArpPacket extends AbstractPacket {
    public int hardwareType;
    public int protocolType;
    public int hardwareSize;
    public int protocolSize;
    public int opcode;
    public ByteArray senderMac;
    public ByteArray senderIp;
    public ByteArray targetMac;
    public ByteArray targetIp;

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
        if (bytes.length() < 7) {
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
    public String toString() {
        if (protocolType == Consts.ARP_PROTOCOL_TYPE_IP) {
            if (opcode == Consts.ARP_PROTOCOL_OPCODE_REQ) { // request
                if (targetIp != null && targetIp.length() == 4 && senderIp != null && senderIp.length() == 4) {
                    return "ArpPacket(" +
                        "who has " + Utils.ipStr(targetIp.toJavaArray()) + "?" +
                        " tell " + Utils.ipStr(senderIp.toJavaArray())
                        + " senderMac=" + runAvoidNull(() -> senderMac.toHexString(), "null")
                        + " targetMac=" + runAvoidNull(() -> targetMac.toHexString(), "null")
                        + ")";
                }
            } else if (opcode == Consts.ARP_PROTOCOL_OPCODE_RESP) { // response
                if (senderIp != null && senderIp.length() == 4 && senderMac != null) {
                    return "ArpPacket(" +
                        Utils.ipStr(senderIp.toJavaArray()) + " is at " + senderMac.toHexString()
                        + " targetMac=" + runAvoidNull(() -> targetMac.toHexString(), "null")
                        + " targetIp=" + runAvoidNull(() -> targetIp.toHexString(), "null")
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
            ", senderMac=" + runAvoidNull(() -> senderMac.toHexString(), "null") +
            ", senderIp=" + runAvoidNull(() -> senderIp, "null") +
            ", targetMac=" + runAvoidNull(() -> targetMac, "null") +
            ", targetIp=" + runAvoidNull(() -> targetIp, "null") +
            '}';
    }
}
