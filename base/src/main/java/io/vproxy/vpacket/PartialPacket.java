package io.vproxy.vpacket;

public interface PartialPacket {
    int LEVEL_KEY_FIELDS = 0;
    int LEVEL_HANDLED_FIELDS = 1;

    String initPartial(PacketDataBuffer raw);

    String initPartial(int level);
}
