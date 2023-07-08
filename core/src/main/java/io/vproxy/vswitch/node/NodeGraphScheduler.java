package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.vswitch.PacketBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeGraphScheduler {
    public final NodeGraph graph;
    private boolean generated = false;
    private boolean isScheduling = false;
    private final Map<Node, RingQueue<PacketBuffer>> nextMap = new HashMap<>();

    public NodeGraphScheduler(NodeGraph graph) {
        this.graph = graph;
    }

    public void schedule(PacketBuffer pkb) {
        if (isScheduling) {
            add(pkb);
        } else {
            setTrace(pkb);
            isScheduling = true;
            try {
                doSchedule(pkb);
                postHandle();
            } finally {
                isScheduling = false;
            }
        }
    }

    public void schedule(List<PacketBuffer> ls) {
        if (ls.isEmpty()) {
            return;
        }
        if (isScheduling) {
            for (var pkb : ls) {
                add(pkb);
            }
            return;
        }
        var first = ls.get(0);
        for (int i = 1; i < ls.size(); ++i) {
            add(ls.get(i));
        }
        schedule(first);
    }

    protected void packetDroppedOrStolen(PacketBuffer pkb) {
    }

    protected boolean tracePacket(PacketBuffer pkb) {
        return false;
    }

    private void add(PacketBuffer pkb) {
        if (pkb.next == null) {
            assert Logger.lowLevelDebug("pkb.next doesn't exist");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.resetIndent();
                pkb.debugger.append("dropped: next node is not set");
                pkb.debugger.newLine();
            }
            packetDroppedOrStolen(pkb);
            return;
        }
        setTrace(pkb);
        RingQueue<PacketBuffer> q = nextMap.get(pkb.next);
        if (q == null) {
            q = new RingQueue<>();
            nextMap.put(pkb.next, q);
        }
        q.add(pkb);
        generated = true;
    }

    private void setTrace(PacketBuffer pkb) {
        if (pkb.debugger.isDebugOn()) {
            return;
        }
        if (tracePacket(pkb)) {
            pkb.debugger.setDebugOn(true);
        }
    }

    public boolean isGenerated() {
        return generated;
    }

    private void doSchedule(PacketBuffer pkb) {
        loop:
        while (true) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.resetIndent();
            }
            var next = pkb.next;
            if (next == null) {
                assert Logger.lowLevelDebug("no next node for redirecting the pkb " + pkb);
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.append("dropped: next node is not set");
                    pkb.debugger.newLine();
                }
                packetDroppedOrStolen(pkb);
                break;
            }
            assert Logger.lowLevelDebug("next node is " + next.name);

            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("node: ").append(next.name);
                pkb.debugger.newLine();
            }

            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.incIndent();
                pkb.debugger.incIndent();
            }
            resetForNewPacket();
            pkb.next = null;
            var res = next.handle(pkb, this);
            assert Logger.lowLevelDebug("handle result: " + res + ", next: " + (pkb.next == null ? "null" : pkb.next.name));
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.decIndent();
                pkb.debugger.append("result: ").append(res);
                pkb.debugger.newLine();
            }
            switch (res) {
                case PASS:
                case PICK:
                    assert Logger.lowLevelDebug("picked");

                    scheduleNextMap(next);
                    continue;
                case CONTINUE:
                case DROP:
                    assert Logger.lowLevelDebug("dropped");
                    packetDroppedOrStolen(pkb);

                    scheduleNextMap(next);
                    break loop;
                case STOLEN:
                    assert Logger.lowLevelDebug("stolen");
                    packetDroppedOrStolen(pkb);

                    scheduleNextMap(next);
                    break loop;
            }
        }
    }

    private void scheduleNextMap(Node next) {
        var q = nextMap.get(next);
        if (q == null || q.isEmpty()) {
            return;
        }
        PacketBuffer pkb;
        while ((pkb = q.poll()) != null) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.resetIndent();
                pkb.debugger.append("node: ").append(next.name);
                pkb.debugger.newLine();
                pkb.debugger.incIndent();
                pkb.debugger.incIndent();
            }
            resetForNewPacket();
            pkb.next = null;
            var res = next.handle(pkb, this);
            assert Logger.lowLevelDebug("handle result: " + res + ", next: " + (pkb.next == null ? "null" : pkb.next.name));
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.decIndent();
                pkb.debugger.append("result: ").append(res);
                pkb.debugger.newLine();
            }
            switch (res) {
                case PASS:
                case PICK:
                    assert Logger.lowLevelDebug("picked");
                    if (pkb.next == null) {
                        assert Logger.lowLevelDebug("no next node for redirecting the pkb " + pkb);
                        if (pkb.debugger.isDebugOn()) {
                            pkb.debugger.append("dropped: next node is not set");
                            pkb.debugger.newLine();
                        }
                        packetDroppedOrStolen(pkb);
                    } else {
                        add(pkb);
                    }
                    continue;
                case CONTINUE:
                case DROP:
                    assert Logger.lowLevelDebug("dropped");
                    packetDroppedOrStolen(pkb);
                    break;
                case STOLEN:
                    assert Logger.lowLevelDebug("stolen");
                    packetDroppedOrStolen(pkb);
                    break;
            }
        }
    }

    private void resetForNewPacket() {
        generated = false;
    }

    private void postHandle() {
        while (true) {
            var handled = false;
            for (var q : new ArrayList<>(nextMap.values())) {
                var pkb = q.poll();
                if (pkb == null) {
                    continue;
                }
                doSchedule(pkb);
                handled = true;
            }
            if (!handled) {
                break;
            }
        }
    }
}
