package io.vproxy.vswitch.iface;

public interface LocalSideVrfGetterSetter {
    int getLocalSideVrf(int hintVrf);

    void setLocalSideVrf(int vrf);
}
