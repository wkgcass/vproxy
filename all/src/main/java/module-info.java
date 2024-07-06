module io.vproxy.all {
    requires org.graalvm.nativeimage;
    requires transitive io.vproxy.dep;
    requires transitive io.vproxy.base;
    requires transitive io.vproxy.lib;
    requires transitive io.vproxy.core;
    requires transitive io.vproxy.extended;
    requires transitive io.vproxy.app;
}
