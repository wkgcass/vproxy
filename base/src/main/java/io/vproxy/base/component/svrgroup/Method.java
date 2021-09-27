package vproxy.base.component.svrgroup;

public enum Method {
    wrr,
    wlc,
    source, // consistent hashing with source ip address
}
