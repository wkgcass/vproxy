package vproxybase.dhcp.options;

import vproxybase.dhcp.DHCPOption;
import vproxybase.util.ByteArray;
import vproxybase.util.Consts;

public class MessageTypeOption extends DHCPOption {
    public byte msgType;

    public MessageTypeOption() {
        this(0);
    }

    public MessageTypeOption(int msgType) {
        this.msgType = (byte) msgType;
        super.type = Consts.DHCP_OPT_TYPE_MSG_TYPE;
        super.len = 1;
    }

    @Override
    public ByteArray serialize() {
        content = ByteArray.from(msgType);
        return super.serialize();
    }

    @Override
    public int deserialize(ByteArray arr) throws Exception {
        int n = super.deserialize(arr);
        if (n != 3) {
            throw new Exception("dhcp option (message type) should have total length 3, but got " + n);
        }
        msgType = content.get(0);
        return n;
    }

    @Override
    public String toString() {
        return "MessageTypeOption{" +
            "msgType=" + msgType +
            '}';
    }
}
