package vswitch.packet;

import vproxy.util.ByteArray;
import vproxy.util.Utils;
import vproxy.util.crypto.Aes256Key;
import vproxy.util.crypto.StreamingCFBCipher;
import vswitch.util.Consts;

import java.util.Random;

/*
 * +---------+-----------+----------+---------------+
 * | IV (16) | MAGIC (4) | TYPE (2) |     VXLAN     |
 * +---------+-----------+----------+---------------+
 */
public class VProxySwitchPacket extends AbstractPacket {
    public int magic;
    public int type;
    public VXLanPacket vxlan;

    private final Aes256Key key;

    public VProxySwitchPacket(Aes256Key key) {
        this.key = key;
    }

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 22) {
            return "input packet length too short for a vproxy switch packet";
        }
        byte[] iv = bytes.sub(0, 16).toJavaArray();
        byte[] rawBytes = bytes.toJavaArray();
        StreamingCFBCipher cipher = new StreamingCFBCipher(key, false, iv);
        magic = ByteArray.from(
            cipher.update(rawBytes, 16, 4)
        ).int32(0);
        if (magic != Consts.VPROXY_SWITCH_MAGIC) {
            return "decryption failed: wrong magic: " + Utils.toHexString(magic);
        }
        ByteArray result = ByteArray.from(
            cipher.update(rawBytes, 20, rawBytes.length - 20)
        );
        type = result.uint16(0);
        if (type == Consts.VPROXY_SWITCH_TYPE_VXLAN) {
            ByteArray other = result.sub(2, result.length() - 2);
            VXLanPacket packet = new VXLanPacket();
            String err = packet.from(other);
            if (err != null) {
                return err;
            }
            vxlan = packet;
        } else if (type == Consts.VPROXY_SWITCH_TYPE_PING) {
            if (result.length() != 2) {
                return "extra bytes for a vproxy switch ping packet: " + (result.length() - 2);
            }
        } else {
            return "invalid type for vproxy switch packet: " + type;
        }
        return null;
    }

    @Override
    public ByteArray getRawPacket() {
        byte[] ivBytes = new byte[16];
        Random rand = new Random();
        rand.nextBytes(ivBytes);

        ByteArray iv = ByteArray.from(ivBytes).copy();
        ByteArray other = ByteArray.allocate(6);
        other.int32(0, magic);
        other.int16(4, type);
        if (vxlan != null) {
            other = other.concat(vxlan.getRawPacket());
        }
        byte[] otherBytes = other.toJavaArray();
        StreamingCFBCipher cipher = new StreamingCFBCipher(key, true, ivBytes);
        byte[] encrypted = cipher.update(otherBytes, 0, otherBytes.length);
        return iv.concat(ByteArray.from(encrypted));
    }

    @Override
    public String toString() {
        return "VProxySwitchPacket{" +
            "magic=" + Utils.toHexString(magic) +
            ", type=" + type +
            ", vxlan=" + vxlan +
            // ", key=" + key +
            '}';
    }
}
