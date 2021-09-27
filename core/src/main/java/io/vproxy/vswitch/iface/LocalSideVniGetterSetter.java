package io.vproxy.vswitch.iface;

public interface LocalSideVniGetterSetter {
    int getLocalSideVni(int hint);

    void setLocalSideVni(int serverSideVni);
}
