package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.vswitch.PacketBuffer;

import java.util.function.Consumer;

public class TraceDebugger {
    private StringBuilder builder;
    private int indent = 0;
    private boolean isNewLine = true;
    private boolean debugOn = false;

    public TraceDebugger() {
    }

    public boolean isDebugOn() {
        return debugOn;
    }

    public void setDebugOn(boolean debugOn) {
        this.debugOn = debugOn;
    }

    private StringBuilder builder() {
        if (builder == null) {
            builder = new StringBuilder();
        }
        return builder;
    }

    public void incIndent() {
        newLine();
        indent += 2;
    }

    public void decIndent() {
        newLine();
        indent -= 2;
    }

    public void resetIndent() {
        newLine();
        indent = 0;
    }

    public TraceDebugger append(Object msg) {
        return append(msg == null ? "null" : msg.toString());
    }

    public TraceDebugger append(String msg) {
        if (isNewLine) {
            if (indent > 0) {
                builder().append(" ".repeat(indent));
            }
        }
        isNewLine = false;
        builder().append(msg);
        return this;
    }

    public void append(String fmt, Object... args) {
        append(String.format(fmt, args));
    }

    public void line(Consumer<TraceDebugger> f) {
        f.accept(this);
        newLine();
    }

    public void newNode(String node, PacketBuffer pkb) {
        if (!isDebugOn()) {
            return;
        }
        assert Logger.lowLevelDebug(node + ": " + pkb);
        resetIndent();
        append("node: ").append(node);
        incIndent();
        newLine();
    }

    public void newLine() {
        if (isNewLine) {
            return;
        }
        isNewLine = true;
        builder().append("\n");
    }

    @Override
    public String toString() {
        if (builder == null) return "";
        return builder.toString();
    }
}
