package vswitch.util;

import vproxy.util.crypto.Aes256Key;

public class UserInfo {
    public final String user;
    public final Aes256Key key;
    public final String pass;
    public final int vni;

    public UserInfo(String user, Aes256Key key, String pass, int vni) {
        this.user = user;
        this.key = key;
        this.pass = pass;
        this.vni = vni;
    }
}
