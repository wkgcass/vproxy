package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Utils;
import vproxy.base.util.crypto.Aes256Key;
import vproxy.base.util.crypto.StreamingCFBCipher;

import java.util.Base64;
import java.util.Random;
import java.util.function.Function;

/*
 * +----------+---------+-----------+----------+---------------+
 * | USER (6) | IV (16) | MAGIC (4) | TYPE (2) |     VXLAN     |
 * +----------+---------+-----------+----------+---------------+
 * encode user with base64 to get the string form user name
 * decode the user name string with base64 to get the binary form user name
 * the user string must be 8 chars, a-zA-Z0-9, however a default padding may be added
 */
public class VProxyEncryptedPacket extends AbstractPacket {
    private String user;
    private int magic;
    private int type;
    private VXLanPacket vxlan;

    private final Function<String, Aes256Key> keyProvider;

    public VProxyEncryptedPacket(Function<String, Aes256Key> keyProvider) {
        this.keyProvider = keyProvider;
    }

    public VProxyEncryptedPacket(Aes256Key key) {
        this(unused -> key);
    }

    @Override
    public String from(ByteArray bytes) {
        return from(bytes, false);
    }

    public String from(ByteArray bytes, boolean skipIPPacket) {
        if (bytes.length() < 28) {
            return "input packet length too short for a vproxy switch packet";
        }
        byte[] userBytes = bytes.sub(0, 6).toJavaArray();
        user = Base64.getEncoder().encodeToString(userBytes).replace("=", "");
        Aes256Key key = keyProvider.apply(user);
        if (key == null) {
            return "cannot get key for user " + user;
        }

        byte[] iv = bytes.sub(6, 16).toJavaArray();
        byte[] rawBytes = bytes.toJavaArray();
        StreamingCFBCipher cipher = new StreamingCFBCipher(key, false, iv);
        magic = ByteArray.from(
            cipher.update(rawBytes, 22, 4)
        ).int32(0);
        if (magic != Consts.VPROXY_SWITCH_MAGIC) {
            return "decryption failed: wrong magic: " + Utils.toHexString(magic);
        }
        ByteArray result = ByteArray.from(
            cipher.update(rawBytes, 26, rawBytes.length - 26)
        );
        type = result.uint16(0);
        if (type == Consts.VPROXY_SWITCH_TYPE_VXLAN) {
            ByteArray other = result.sub(2, result.length() - 2);
            VXLanPacket packet = new VXLanPacket();
            String err = packet.from(other, skipIPPacket);
            if (err != null) {
                return err;
            }
            vxlan = packet;
            vxlan.recordParent(this);
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
    protected ByteArray buildPacket() {
        byte[] x = Base64.getDecoder().decode(user);
        if (x.length != 6) {
            throw new IllegalArgumentException("the user decoded binary length is not 6");
        }
        Aes256Key key = keyProvider.apply(user);
        if (key == null) {
            throw new IllegalArgumentException("cannot retrieve key for user " + user);
        }
        ByteArray userB = ByteArray.from(x);

        byte[] ivBytes = Utils.allocateByteArrayInitZero(16);
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
        return userB.concat(iv).concat(ByteArray.from(encrypted));
    }

    @Override
    protected void updateChecksum() {
        vxlan.checkAndUpdateChecksum();
    }

    @Override
    public String description() {
        return "vproxy"
            + ",user=" + user
            + ",type=" + type
            + "," + vxlan.description();
    }

    @Override
    public String toString() {
        return "VProxyEncryptedPacket{" +
            "user=" + user +
            ", magic=" + Utils.toHexString(magic) +
            ", type=" + type +
            ", vxlan=" + vxlan +
            // ", key=" + key +
            '}';
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        clearRawPacket();
        this.user = user;
    }

    public int getMagic() {
        return magic;
    }

    public void setMagic(int magic) {
        clearRawPacket();
        this.magic = magic;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        clearRawPacket();
        this.type = type;
    }

    public VXLanPacket getVxlan() {
        return vxlan;
    }

    public void setVxlan(VXLanPacket vxlan) {
        clearRawPacket();
        this.vxlan = vxlan;
    }
}
