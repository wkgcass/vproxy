package vproxy.base.component.elgroup;

public interface EventLoopGroupAttach {
    String id();

    void onEventLoopAdd();

    void onClose();
}
