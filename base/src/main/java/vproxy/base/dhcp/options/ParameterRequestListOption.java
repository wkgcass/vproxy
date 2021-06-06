package vproxy.base.dhcp.options;

import vproxy.base.dhcp.DHCPOption;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;

import java.util.LinkedList;
import java.util.List;

public class ParameterRequestListOption extends DHCPOption {
    public final List<Byte> params;

    public ParameterRequestListOption() {
        this(null);
    }

    public ParameterRequestListOption(List<Byte> params) {
        this.params = params == null ? new LinkedList<>() : new LinkedList<>(params);
        this.type = Consts.DHCP_OPT_TYPE_PARAM_REQ_LIST;
    }

    public ParameterRequestListOption add(byte param) {
        params.add(param);
        return this;
    }

    @Override
    public ByteArray serialize() {
        this.len = params.size();
        this.content = ByteArray.allocate(len);
        int idx = 0;
        for (byte b : params) {
            content.set(idx, b);
            ++idx;
        }
        return super.serialize();
    }

    @Override
    public int deserialize(ByteArray arr) throws Exception {
        int n = super.deserialize(arr);
        for (int i = 0; i < content.length(); ++i) {
            params.add(content.get(i));
        }
        return n;
    }

    @Override
    public String toString() {
        return "ParameterRequestListOption{" +
            "params=" + params +
            '}';
    }
}
