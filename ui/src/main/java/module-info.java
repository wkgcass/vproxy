module io.vproxy.ui {
    requires jdk.unsupported;
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires javafx.controls;
    requires transitive io.vproxy.all;
    requires io.vproxy.vfx;

    exports io.vproxy.ui.calculator;
}
