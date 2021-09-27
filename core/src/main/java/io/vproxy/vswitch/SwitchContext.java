package io.vproxy.vswitch;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.stack.NetworkStack;
import io.vproxy.vswitch.util.UserInfo;

import java.util.Collection;
import java.util.function.Supplier;

public class SwitchContext {
    SwitchContext(Switch sw,
                  Supplier<NetworkStack> netStack,
                  LoopRemovalStop loopRemovalStop,
                  SendingPacket sendPacketFunc,
                  GetIfaces getIfacesFunc,
                  GetTable getTableFunc,
                  GetUserInfo getUserInfo,
                  GetSelectorEventLoop getSelectorEventLoopFunc,
                  AlertPacketsArrive alertPacketsArriveFunc,
                  DestroyIface destroyIfaceFunc,
                  InitIface initIfaceFunc,
                  RecordIface recordIfaceFunc) {
        this.sw = sw;
        this.netStack = netStack;
        this.loopRemovalStopFunc = loopRemovalStop;
        this.sendPacketFunc = sendPacketFunc;
        this.getIfacesFunc = getIfacesFunc;
        this.getTableFunc = getTableFunc;
        this.getUserInfoFunc = getUserInfo;
        this.getSelectorEventLoopFunc = getSelectorEventLoopFunc;
        this.alertPacketsArriveFunc = alertPacketsArriveFunc;
        this.destroyIfaceFunc = destroyIfaceFunc;
        this.initIfaceFunc = initIfaceFunc;
        this.recordIfaceFunc = recordIfaceFunc;
    }

    public final Switch sw;
    public final Supplier<NetworkStack> netStack;

    public interface LoopRemovalStop {
        void loopRemovalStop();
    }

    private final LoopRemovalStop loopRemovalStopFunc;

    public void loopRemovalStop() {
        loopRemovalStopFunc.loopRemovalStop();
    }

    public interface SendingPacket {
        void send(PacketBuffer pkb, Iface iface);
    }

    private final SendingPacket sendPacketFunc;

    public void sendPacket(PacketBuffer pkb, Iface toIface) {
        sendPacketFunc.send(pkb, toIface);
    }

    public interface GetIfaces {
        Collection<Iface> getIfaces();
    }

    private final GetIfaces getIfacesFunc;

    public Collection<Iface> getIfaces() {
        return getIfacesFunc.getIfaces();
    }

    public interface GetTable {
        VirtualNetwork getTable(int vni);
    }

    private final GetTable getTableFunc;

    public VirtualNetwork getNetwork(int vni) {
        return getTableFunc.getTable(vni);
    }

    public interface GetUserInfo {
        UserInfo getUserInfo(String user);
    }

    private final GetUserInfo getUserInfoFunc;

    public UserInfo getUserInfo(String user) {
        return getUserInfoFunc.getUserInfo(user);
    }

    public interface GetSelectorEventLoop {
        SelectorEventLoop getSelectorEventLoop();
    }

    private final GetSelectorEventLoop getSelectorEventLoopFunc;

    public SelectorEventLoop getSelectorEventLoop() {
        return getSelectorEventLoopFunc.getSelectorEventLoop();
    }

    public interface DestroyIface {
        void destroyIface(Iface iface);
    }

    private final DestroyIface destroyIfaceFunc;

    public void destroyIface(Iface iface) {
        destroyIfaceFunc.destroyIface(iface);
    }

    public interface InitIface {
        void initIface(Iface iface) throws Exception;
    }

    private final InitIface initIfaceFunc;

    public void initIface(Iface iface) throws Exception {
        initIfaceFunc.initIface(iface);
    }

    public interface AlertPacketsArrive {
        void alertPacketsArrive(RingQueue<PacketBuffer> queue);
    }

    private final AlertPacketsArrive alertPacketsArriveFunc;

    public void alertPacketsArrive(RingQueue<PacketBuffer> queue) {
        alertPacketsArriveFunc.alertPacketsArrive(queue);
    }

    public interface RecordIface {
        void recordIface(Iface iface);
    }

    private final RecordIface recordIfaceFunc;

    public void recordIface(Iface iface) {
        recordIfaceFunc.recordIface(iface);
    }
}
