package vproxy.vswitch.util;

import vproxy.base.util.crypto.Aes256Key;

public class UserInfo {
    public final String user;
    public final Aes256Key key;
    public final String pass;
    public final int vni;
    public int defaultMtu;
    public boolean defaultFloodAllowed;

    public UserInfo(String user, Aes256Key key, String pass, int vni,
                    int defaultMtu, boolean defaultFloodAllowed) {
        this.user = user;
        this.key = key;
        this.pass = pass;
        this.vni = vni;
        this.defaultMtu = defaultMtu;
        this.defaultFloodAllowed = defaultFloodAllowed;
    }
}
