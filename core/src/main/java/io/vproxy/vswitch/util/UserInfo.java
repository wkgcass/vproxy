package io.vproxy.vswitch.util;

import io.vproxy.base.util.crypto.Aes256Key;
import io.vproxy.vswitch.iface.IfaceParams;

public class UserInfo {
    public final String user;
    public final Aes256Key key;
    public final String pass;
    public final int vrf;
    public final IfaceParams defaultIfaceParams;

    public UserInfo(String user, Aes256Key key, String pass, int vrf,
                    IfaceParams defaultIfaceParams) {
        this.user = user;
        this.key = key;
        this.pass = pass;
        this.vrf = vrf;
        this.defaultIfaceParams = defaultIfaceParams;
    }
}
